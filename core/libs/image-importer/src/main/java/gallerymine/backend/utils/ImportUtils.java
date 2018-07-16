package gallerymine.backend.utils;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.services.ProcessService;
import gallerymine.model.ImportSource;
import gallerymine.model.Picture;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ImportUtils {

    public static final String IMPORT_FILE_NAME = "import.mark";
    public static final String IMPORT_TIMESTAMP = "import.mark";
    public static final String FOLDER_SOURCE = "source";
    public static final String FOLDER_DUP = "dup";
    public static final String FOLDER_SIMILAR = "similar";
    public static final String FOLDER_APPROVE = "approve";
    public static final String FOLDER_FAILED = "failed";

    private static Logger log = LoggerFactory.getLogger(ImportUtils.class);

    @Autowired
    public AppConfig appConfig;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ProcessService processService;

    @Autowired
    private ImportRequestRepository requestRepository;

    @Autowired
    private ImportSourceRepository importSourceRepository;

    @Autowired
    private PictureRepository pictureRepository;

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
            timestamp = DateTime.now();
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

    public Path calcFailedPath(Path importPath) {
        return importPath.getParent().resolve(FOLDER_FAILED);
    }

    public void moveFileStructure(Path source, Path target, AtomicLong files, AtomicLong folders) throws IOException {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(source)) {
            for (Path srcFile : directoryStream) {
                if (srcFile.toFile().isDirectory()) {
                    folders.incrementAndGet();
                    if (!appConfig.isDryRunImportMoves()) {
                        FileUtils.moveDirectoryToDirectory(srcFile.toFile(), target.toFile(), true);
                    } else {
                        Path targetFolder = target.resolve(srcFile.getFileName());
                        targetFolder.toFile().mkdirs();
                        moveFileStructure(srcFile, targetFolder, files, folders);
                    }
                } else {
                    files.incrementAndGet();
                    if (!appConfig.isDryRunImportMoves()) {
                        FileUtils.moveFileToDirectory(srcFile.toFile(), target.toFile(), true);
                    } else {
                        // Simply create symlynk
                        Files.createSymbolicLink(target.resolve(srcFile.getFileName()), srcFile);
                    }
                }
            }
        }
    }

    public String prepareImportFolder(boolean enforce, Process process) throws ImportFailedException {
        Path pathExposed = Paths.get(appConfig.getImportExposedRootFolder());
        Path pathToImport = appConfig.getImportRootFolderPath().resolve(DateTime.now().toString("yyyy-MM-dd_HH-mm-ss_SSS"));

        Path pathToImportSource = pathToImport.resolve(FOLDER_SOURCE);
        Path pathToImportDuplicates = pathToImport.resolve(FOLDER_DUP);
        Path pathToImportSimilar = pathToImport.resolve(FOLDER_SIMILAR);
        Path pathToImportApprove = pathToImport.resolve(FOLDER_APPROVE);
        Path pathToImportFailed = pathToImport.resolve(FOLDER_FAILED);

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
            if (!pathToImportFailed.toFile().mkdirs()) {
                log.warn("Failed to create folder for import %s", pathToImportFailed.toFile().getAbsolutePath());
            }

            process.setName("Pictures Folder Import "+pathToImport.getFileName());
            process.setType(ProcessType.IMPORT);
            process.setStarted(DateTime.now());
            process.setStatus(ProcessStatus.PREPARING);
            processRepository.save(process);

            AtomicLong files = new AtomicLong();
            AtomicLong folders = new AtomicLong();

            try {
                moveFileStructure(pathExposed, pathToImportSource, files, folders);


                List<String> notes = new ArrayList<>();
                process.setStatus(ProcessStatus.STARTING);

                notes.add("Prepared for import:");
                if (files.get() > 0) {
                    notes.add(String.format("  Files  : %5d", files.get()));
                }
                if (folders.get() > 0) {
                    notes.add(String.format("  Folders: %5d", folders.get()));
                }
                if (files.get() == 0 && folders.get() == 0) {
                    notes.add("  None");
                }
                process = processService.addNotes(process.getId(), ProcessStatus.STARTING, notes);
            } catch (Exception e) {
                process = processService.retrySave(process.getId(), processEntity -> {
                    processEntity.setStatus(ProcessStatus.FAILED);
                    processEntity.addError("Failed to move data into internal folder. Reason: %s", e.getMessage());
                    processEntity.addNote("Prepared for import:");
                    if (files.get() > 0) {
                        processEntity.addNote("  Files  : %5d", files.get());
                    }
                    if (folders.get() > 0) {
                        processEntity.addNote("  Folders: %5d", folders.get());
                    }
                    if (files.get() == 0 && folders.get() == 0) {
                        processEntity.addNote("  None");
                    }
                    return processEntity;
                });
                throw new ImportFailedException("Failed to index. Reason: Failed to move files from exposed folder. Reason: "+e.getMessage(), e);
            }

//            String pathToIndex = appConfig.relativizePathToImport(pathToImport.resolve(FOLDER_SOURCE)).toString();
            String pathToIndex = pathToImport.getFileName()+"/"+ FOLDER_SOURCE;
            ImportRequest request = requestRepository.findByPath(pathToIndex);

            if ((!enforce) && request != null && request.getStatus() != ImportRequest.ImportStatus.DONE) {
                throw new ImportFailedException("Importing is already in progress");
            }

            return pathToIndex;
        } catch (ImportFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportFailedException("Failed to index. Reason: "+e.getMessage(), e);
        }
    }

    public Path moveToSimilar(Path file, String importPath) throws IOException {
        String relativePath = appConfig.relativizePath(file, appConfig.getImportRootFolderPath().resolve(importPath));
        Path targetPath = Paths.get(importPath);
        targetPath = appConfig.getImportRootFolderPath().resolve(calcSimilarsPath(targetPath));
        Path fullTargetPath = targetPath.resolve(relativePath).getParent();
        if (!appConfig.isDryRunImportMoves() || FileUtils.isSymlink(file.toFile())) {
            FileUtils.moveFileToDirectory(file.toFile(), fullTargetPath.toFile(), true);
        }
        return targetPath;
    }

    public Path moveToDuplicates(Path file, String importPath) throws IOException {
        String relativePath = appConfig.relativizePath(file, appConfig.getImportRootFolderPath().resolve(importPath));
        Path targetPath = Paths.get(importPath);
        targetPath = appConfig.getImportRootFolderPath().resolve(calcDuplicatesPath(targetPath));
        Path fullTargetPath = targetPath.resolve(relativePath).getParent();
        if (!appConfig.isDryRunImportMoves() || FileUtils.isSymlink(file.toFile())) {
            FileUtils.moveFileToDirectory(file.toFile(), fullTargetPath.toFile(), true);
        }
        return targetPath;
    }

    public Path moveToApprove(Path file, String importPath) throws IOException {
        String relativePath = appConfig.relativizePath(file, appConfig.getImportRootFolderPath().resolve(importPath));
        Path targetPath = Paths.get(importPath);
        targetPath = appConfig.getImportRootFolderPath().resolve(calcApprovePath(targetPath));
        Path fullTargetPath = targetPath.resolve(relativePath).getParent();
        if (!appConfig.isDryRunImportMoves() || FileUtils.isSymlink(file.toFile())) {
            FileUtils.moveFileToDirectory(file.toFile(), fullTargetPath.toFile(), true);
        }
        return targetPath;
    }

    public Path moveToFailed(Path file, String importPath) throws IOException {
        String relativePath = appConfig.relativizePath(file, appConfig.getImportRootFolderPath().resolve(importPath));
        Path targetPath = Paths.get(importPath);
        targetPath = calcFailedPath(targetPath);
        targetPath = appConfig.getImportRootFolderPath().resolve(targetPath);
        Path fullTargetPath = targetPath.resolve(relativePath).getParent();
        if (!appConfig.isDryRunImportMoves() || FileUtils.isSymlink(file.toFile())) {
            FileUtils.moveFileToDirectory(file.toFile(), fullTargetPath.toFile(), true);
        }
        return targetPath;
    }
}
