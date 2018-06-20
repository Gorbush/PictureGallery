package gallerymine.backend.utils;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.importer.ImportProcessor;
import gallerymine.backend.pool.ImportRequestPoolManager;
import gallerymine.model.ImportSource;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

@Component
public class ImportUtils {

    public static final String IMPORT_FILE_NAME = "import.mark";
    public static final String IMPORT_TIMESTAMP = "import.mark";
    public static final String FOLDER_SOURCE = "source";
    public static final String FOLDER_DUP = "dup";
    public static final String FOLDER_SIMILAR = "similar";
    public static final String FOLDER_APPROVE = "approve";

    private static Logger log = LoggerFactory.getLogger(ImportUtils.class);

    @Autowired
    public AppConfig appConfig;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ImportRequestPoolManager requestPool;

    @Autowired
    private ImportRequestRepository requestRepository;

    @Autowired
    private ImportProcessor requestProcessor;

    @Autowired
    private ImportSourceRepository importSourceRepository;

    public Path makeNewImportStamp(DateTime importStamp) {
        String stamp = importStamp.toString("yyyy-MM-dd_HH-mm-ss");
        Path importFolder = Paths.get(appConfig.importRootFolder, stamp);
        int index = 0;
        while (importFolder.toFile().exists()) {
            index++;
            importFolder = Paths.get(appConfig.importRootFolder, stamp+"_"+index);
        }
        return importFolder;
    }

    public Path getImportMarkFile(Path path) {
        Path markFile = path.resolve(IMPORT_FILE_NAME);
        return markFile;
    }

    public String getImportMarkFileContent(Path path) {
        Path markFile = getImportMarkFile(path);
        try {
            return FileUtils.readFileToString(markFile.toFile(), "UTF-8");
        } catch (IOException e) {
            log.error("Failed to read the mark file %s", markFile.toFile().getAbsolutePath());
            return null;
        }
    }

    public Path createImportMarkFile(Path path, DateTime importStamp) {
        Path markFile = path.resolve(IMPORT_FILE_NAME);
        if (!markFile.toFile().exists()) {
            String stamp = importStamp.toString("yyyy-MM-dd HH:mm:ss");
            try {
                FileUtils.writeStringToFile(markFile.toFile(), stamp+"\n", "UTF-8");
            } catch (IOException e) {
                log.error("Failed to write to Import Mark File %s", markFile.toFile().getAbsolutePath());
            }
        }
        return markFile;
    }

    public void appendImportMarkFile(Path markFileOrFolder, String text) {
        if (markFileOrFolder.toFile().exists()) {
            Path markFile = markFileOrFolder;
            if (markFileOrFolder.toFile().isDirectory()) {
                markFile = markFileOrFolder.resolve(IMPORT_FILE_NAME);
            }
            try {
                FileUtils.writeStringToFile(markFile.toFile(), text+"\n", "UTF-8", true);
            } catch (IOException e) {
                log.error("Failed to append to Import Mark File %s", markFile.toFile().getAbsolutePath());
            }
        }
    }

    public Path generatePicThumbName(String fileName, DateTime timestamp) {
        if (timestamp == null) {
            timestamp = new DateTime();
        }

        String picFolderName = timestamp.toString("/yyyy/MM/dd/SSSSSSSSS"); //SSSSSSSSS
        Path thumbFolder = Paths.get(appConfig.getThumbsRootFolder(), picFolderName);

        if (!thumbFolder.toFile().exists()) {
            boolean created = thumbFolder.toFile().mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create a folder for thumbnail path='" + thumbFolder.toFile().getAbsolutePath() + "'");
            }
        }

        String picFileName = timestamp.getMillis()+"_"+fileName;

        Path thumbPath = thumbFolder.resolve(picFileName+".jpg");
        int index = 0;
        while(thumbPath.toFile().exists()) {
            index++;
            thumbPath = thumbFolder.resolve(picFileName+"_"+index+".jpg");
        }
        return thumbPath;
    }

    public Path calcSimilarsPath(Path importPath) {
        return importPath.getParent().resolve(FOLDER_SIMILAR);
    }

    public Path calcDuplicatesPath(Path importPath) {
        return importPath.getParent().resolve(FOLDER_DUP);
    }

    public Path calcApprovePath(Path importPath) {
        return importPath.getParent().resolve(FOLDER_APPROVE);
    }

    public ImportRequest prepareImportFolder(boolean enforce) throws ImportFailedException {
        Path pathExposed = Paths.get(appConfig.getImportExposedRootFolder());
        Path pathToImport = Paths.get(appConfig.getImportRootFolder(), DateTime.now().toString("yyyy-MM-dd_HH-mm-ss_SSS"));

        Path pathToImportSource = pathToImport.resolve(FOLDER_SOURCE);
        Path pathToImportDuplicates = pathToImport.resolve(FOLDER_DUP);
        Path pathToImportSimilar = pathToImport.resolve(FOLDER_SIMILAR);
        Path pathToImportApprove = pathToImport.resolve(FOLDER_APPROVE);

        Process process = new Process();
        try {
            if (!pathToImportSource.toFile().mkdirs()) {
                log.warn("Failed to create folder for import %s", pathToImportSource.toFile().getAbsolutePath());
            }
            if (!pathToImportDuplicates.toFile().mkdirs()) {
                log.warn("Failed to create folder for import %s", pathToImportDuplicates.toFile().getAbsolutePath());
            }
            if (!pathToImportApprove.toFile().mkdirs()) {
                log.warn("Failed to create folder for import %s", pathToImportApprove.toFile().getAbsolutePath());
            }
            if (!pathToImportSimilar.toFile().mkdirs()) {
                log.warn("Failed to create folder for import %s", pathToImportSimilar.toFile().getAbsolutePath());
            }

            process.setName("Pictures Folder Import "+pathToImport.getFileName());
            process.setType(ProcessType.IMPORT);
            process.setStarted(DateTime.now());
            process.setStatus(ProcessStatus.PREPARING);
            processRepository.save(process);

            int files = 0;
            int folders = 0;
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pathExposed)) {
                for (Path srcFile : directoryStream) {
                    if (srcFile.toFile().isDirectory()) {
                        folders++;
                        FileUtils.moveDirectoryToDirectory(srcFile.toFile(), pathToImportSource.toFile(), true);
                    } else {
                        files++;
                        FileUtils.moveFileToDirectory(srcFile.toFile(), pathToImportSource.toFile(), true);
                    }
                }
                process.setStatus(ProcessStatus.STARTING);
                process.addError("Prepared for import:");
                if (files > 0) {
                    process.addError("  Files  : %5d", files);
                }
                if (folders > 0) {
                    process.addError("  Folders: %5d", folders);
                }
                if (files == 0 && folders == 0) {
                    process.addError("  None");
                }
                processRepository.save(process);
            } catch (Exception e) {
                process.setStatus(ProcessStatus.FAILED);
                process.addError("Failed to move data into internal folder. Reason: %s", e.getMessage());
                process.addError("Prepared for import:");
                if (files > 0) {
                    process.addError("  Files  : %5d", files);
                }
                if (folders > 0) {
                    process.addError("  Folders: %5d", folders);
                }
                if (files == 0 && folders == 0) {
                    process.addError("  None");
                }
                processRepository.save(process);
                throw new ImportFailedException("Failed to index. Reason: Failed to move files from exposed folder. Reason: "+e.getMessage(), e);
            }

            String pathToIndex = pathToImport.resolve(FOLDER_SOURCE).toAbsolutePath().toString();
            ImportRequest request = requestRepository.findByPath(pathToIndex);

            if ((!enforce) && request != null && request.getStatus() != ImportRequest.ImportStatus.DONE) {
                throw new ImportFailedException("Importing is already in progress");
            }

            request = requestProcessor.registerNewImportFolderRequest(pathToIndex, null, process.getId());
            requestPool.executeRequest(request);
//            requestProcessor.processRequest(request, process);

            return request;
        } catch (ImportFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportFailedException("Failed to index. Reason: "+e.getMessage(), e);
        }
    }

    public Collection<ImportSource> findSimilar(String fileName, Long fileSize) {
        Collection<ImportSource> found = importSourceRepository.findByFileNameAndSize(fileName, fileSize);
        return found;
    }

    public Collection<ImportSource> findDuplicates(String fileName, Long fileSize) {
        Collection<ImportSource> found = importSourceRepository.findByFileNameAndSize(fileName, fileSize);
        return found;
    }

    public void moveToSimilar(Path file, String importPath) throws IOException {
        String absolutePath = file.toFile().getAbsolutePath();
        String relativePath = appConfig.relativizePath(absolutePath, importPath);
        Path similarPath = Paths.get(importPath);
        similarPath = calcSimilarsPath(similarPath);
        similarPath = similarPath.resolve(relativePath);
        FileUtils.moveFileToDirectory(file.toFile(), similarPath.toFile(), true);
    }

    public void moveToDuplicates(Path file, String importPath) throws IOException {
        String absolutePath = file.toFile().getAbsolutePath();
        String relativePath = appConfig.relativizePath(absolutePath, importPath);
        Path duplicatePath = Paths.get(importPath);
        duplicatePath = calcDuplicatesPath(duplicatePath);
        duplicatePath = duplicatePath.resolve(relativePath);
        FileUtils.moveFileToDirectory(file.toFile(), duplicatePath.toFile(), true);
    }


}
