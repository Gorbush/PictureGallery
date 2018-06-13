package gallerymine.agents.indexing;

import sun.net.ConnectionResetException;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.lang.System.exit;

public class IndexingAgent {

    static String server = "http://localhost:8070/";
    static String CONTROLLER_FILES = "files/";
    static String CONTROLLER_PROCESSES = "processes/";

    public enum FilePutResult {
        OK,
        ALREADY_EXISTS,
        NOT_ACCESSIBLE,
        BAD_REQUEST,
        FAILED
    }

    static class IDResult {
        static final String NOT_ACCESSIBLE = "NOT_ACCESSIBLE";
        static final String BAD_REQUEST = "BAD_REQUEST";
        static final String FAILED = "FAILED";

        public static boolean isId(String text) {
            return text != null && text.length() == 24;
        }
    }

    private static void log(String message, Object... params) {
        if (params != null && params.length > 0) {
            System.out.println(LINE_START + String.format(message, params));
        } else {
            System.out.println(LINE_START + message);
        }
    }

    private static void logError(String message, Object... params) {
        if (params != null && params.length > 0) {
            System.err.println(LINE_START + String.format(message, params));
        } else {
            System.err.println(LINE_START + message);
        }
    }

    private static String formatNumber(long number) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        symbols.setGroupingSeparator(',');

        DecimalFormat formatter = new DecimalFormat("###,###.##", symbols);
        return formatter.format(number);
    }

    public void processFolder(String storage, String path) throws IOException {
        Path pwd = Paths.get(path);
        long started = System.currentTimeMillis();
        try {
            log("Processing folder %s", pwd.toAbsolutePath().toString());
            processFolder(storage, pwd);
        } finally {
            long ended = System.currentTimeMillis();
            log("Processing took %s", millisToIntervalSeconds(ended-started));
        }
    }

    public static String getCurrentTimeStamp() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
        return format.format(LocalDateTime.now());
    }

    private static int slashStep = 0;
    private static long slashStepUpdated = System.currentTimeMillis();
    private static final long UPDATE_INTERVAL = 2000;
    private static final long PROGRESS_UPDATE_INTERVAL = 10000;
    private static String LINE_START = "\r";

    private void rotatingSlash(String message) {
        long now = System.currentTimeMillis();
        if (now > UPDATE_INTERVAL+slashStepUpdated) {
            String chars = "/-\\|";
            int step = ++slashStep;
            if (step > 3) {
                step = 0;
                slashStep = 0;
            }

            System.out.print(LINE_START + chars.charAt(step) + " " + message);
            slashStepUpdated = now;
        }
    }

    private static String millisToInterval(long millis) {
        if (millis == 0) {
            return "???";
        }
        Instant instant = Instant.ofEpochMilli ( millis );
        ZonedDateTime zdt = ZonedDateTime.ofInstant ( instant , ZoneOffset.UTC );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern ( "HH:mm:ss.SSS" );
        String output = formatter.format ( zdt );
        return output;
    }

    private static String millisToIntervalSeconds(long millis) {
        if (millis == 0) {
            return "???";
        }
        Instant instant = Instant.ofEpochMilli ( millis );
        ZonedDateTime zdt = ZonedDateTime.ofInstant ( instant , ZoneOffset.UTC );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern ( "HH:mm:ss" );
        String output = formatter.format ( zdt ).replaceAll("^[0:]+", "");
        if (output.isEmpty()) {
            output = "0";
        }
        if (!output.contains(":")) {
            output += " sec";
        }
        return output;
    }

    private class LineContext {
        long sourceFileSize;

        AtomicInteger lineIndex = new AtomicInteger(0);
        AtomicLong processed = new AtomicLong(0);
        AtomicInteger processedPercentage = new AtomicInteger(0);
        AtomicLong foldersProcessed = new AtomicLong(0);
        AtomicLong linesSent = new AtomicLong(0);
        AtomicLong putOk = new AtomicLong(0);
        AtomicLong putExists = new AtomicLong(0);
        AtomicLong putBad = new AtomicLong(0);
        AtomicLong putFail = new AtomicLong(0);
        AtomicLong linesSkipped = new AtomicLong(0);

        String errorFolder = "";
        BufferedWriter writerError;
        BufferedWriter writerLog;
        Path fileDone;

        String currentFolder = "";
        long started;
        String storage;
        String lsFileName;
        long skipLines = 0;
        long lastProgressUpdate = System.currentTimeMillis();
        AtomicInteger percentDone = new AtomicInteger(0);

        String status;
        public String indexProcessId;
    }

    private class LineConsumer implements Consumer<String> {

        LineContext context;

        public LineConsumer(LineContext context) {
            this.context = context;
        }

        private void writeDoneFile(int currentLine, long processedSize, String message) {
            long now = System.currentTimeMillis();
            int newPerc = (int) (processedSize * 100 / context.sourceFileSize);
            context.percentDone.set(newPerc);
            if (now > PROGRESS_UPDATE_INTERVAL+context.lastProgressUpdate) {
                context.lastProgressUpdate = now;
            } else {
                int oldPerc = context.processedPercentage.getAndSet(newPerc);
                if (newPerc == oldPerc) {
                    return;
                }
            }

            try {
                context.writerError.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            long rate = 0;
            long sent = context.linesSent.get();
            long spentTime = System.currentTimeMillis() - context.started;
            if (sent > 0 && spentTime > 1000) {
                rate = sent / (spentTime / 1000);
            }

            String status = String.format("  %3d%%  rate:%4d call/sec Estimate: %s " +
                                          " SKIP : %6d  OK: %6d    EXIST: %6d    FAIL: %6d    BAD: %6d",
                    newPerc, rate, millisToInterval(newPerc == 0 ? 0 : (spentTime * 100 / newPerc)),
                    context.linesSkipped.get(), context.putOk.get(), context.putExists.get(), context.putFail.get(), context.putBad.get());

            context.status = status;

            rotatingSlash(millisToIntervalSeconds(spentTime) + status);
            if (currentLine > context.skipLines) {
                try {
                    Files.write(context.fileDone, (String.valueOf(currentLine-1) + "\n" + status + (message!=null?message:"") ).getBytes());
                } catch (IOException e) {
                }
            }
        }

        public void accept(String line) {
            long spentTime = System.currentTimeMillis() - context.started;

            rotatingSlash(millisToIntervalSeconds(spentTime) + (context.status == null ? "" : context.status ));
            long processedSize = context.processed.addAndGet(line.getBytes().length);

            int currentLine = context.lineIndex.getAndIncrement();

            writeDoneFile(currentLine, processedSize, null);

            if (line.matches("^\\./.*")) {
                // this is current processing folder line - saving
                context.currentFolder = line.substring(2, line.length() - 2);
                context.foldersProcessed.incrementAndGet();
                return;
            }

            if (currentLine < context.skipLines) {
                // skip line as it was already processed (basing on the done file)
                context.linesSkipped.incrementAndGet();
                return;
            }

            if (line.matches("^d.*")) {
                // this is folder line - skipping
                return;
            }
            if (!line.matches("^-.*")) {
                // this is not a regular file line - skipping
                return;
            }
            // regular file
            long fileSize = -1;
            String fileDate = "DATE";
            String fileTime = "TIME";
            String fileName = "NAME";
            try {
                Scanner scanner = new Scanner(line);
                scanner.useDelimiter("\\s+");
                scanner.next(); // premissions
                scanner.next(); // id
                scanner.next(); // username
                scanner.next(); // groupName
                fileSize = scanner.nextLong(); // size
                fileDate = scanner.next(); // date
                fileTime = scanner.next(); // time
                fileName = scanner.nextLine().trim(); // file name
                if (fileName.endsWith("*")) {
                    fileName = fileName.substring(0, fileName.length() - 1);
                }
                String fullPath = (context.currentFolder.length() > 0 ? (context.currentFolder + "/") : "") + fileName;
                context.linesSent.incrementAndGet();
//                FilePutResult result = callPutFile(context.storage, fileSize, fileDate + "T" + fileTime, context.currentFolder, fileName);
                FilePutResult result = callPostFile(context.indexProcessId, context.storage, fileSize, fileDate + "T" + fileTime, context.currentFolder, fileName);
                if (FilePutResult.NOT_ACCESSIBLE.equals(result)) {
                    throw new ConnectException("Server is not accessible : " + server);
                }
                if (FilePutResult.OK.equals(result)) context.putOk.incrementAndGet();
                if (FilePutResult.ALREADY_EXISTS.equals(result)) context.putExists.incrementAndGet();
                if (FilePutResult.FAILED.equals(result)) context.putFail.incrementAndGet();
                if (FilePutResult.BAD_REQUEST.equals(result)) context.putBad.incrementAndGet();
                context.writerLog.write(String.format("%15s %s\n", result, fullPath));
                if (FilePutResult.BAD_REQUEST.equals(result) || FilePutResult.FAILED.equals(result)) {
                    if (!context.errorFolder.equals(context.currentFolder)) {
                        context.writerError.write(String.format("./%s:\n",context.currentFolder));
                        context.errorFolder = context.currentFolder;
                    }
                    context.writerError.write(
                            String.format("-file %s username group %d %s %s %s\n",
                                    result.name(), fileSize, fileDate, fileTime, fileName));
                }
            } catch (NoSuchElementException e) {
                String message = String.format("Failed processing line %5d from file %s. Reason: %s", currentLine, context.lsFileName, e.getMessage());
                logError(message);
                writeDoneFile(currentLine, context.percentDone.get(), message);
                throw new RuntimeException(e.getMessage(), e);
            } catch (ConnectException e) {
                String message = String.format("Failed processing line %5d from file %s. Reason: %s", currentLine, context.lsFileName, e.getMessage());
                logError(message);
                writeDoneFile(currentLine, context.percentDone.get(), message);
                throw new RuntimeException(e.getMessage(), e);
            } catch (ConnectionResetException e) {
                String message = String.format("Failed processing line %5d from file %s. Reason: %s", currentLine, context.lsFileName, e.getMessage());
                logError(message);
                writeDoneFile(currentLine, context.percentDone.get(), message);
                throw new RuntimeException(e.getMessage(), e);
            } catch (SocketException e) {
                String message = String.format("Failed processing line %5d from file %s. Reason: %s", currentLine, context.lsFileName, e.getMessage());
                logError(message);
                writeDoneFile(currentLine, context.percentDone.get(), message);
                throw new RuntimeException(e.getMessage(), e);
            } catch (Exception e) {
                String message = String.format("Failed processing line %5d from file %s. Reason: %s", currentLine, context.lsFileName, e.getMessage());
                logError(message);
                writeDoneFile(currentLine, context.percentDone.get(), message);
                try {
                    context.writerError.write(String.format("-file %s username group %d %s %s %s\n",
                            "FAIL", fileSize, fileDate, fileTime, fileName));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                e.printStackTrace();
            }
        }
    }

    /** File should be in the specific format, generated using the following command:
     * <ul>
     *     <li><b>Linux: </b>ls -l -D "%F %T" -F -R > INDEX</li>
     *     <li><b>MacOS: </b> ???? </li>
     * </ul>
     *
     * **/
    public void processLSFile(String storage, String lsFileName) throws IOException {
        Path lsFile = Paths.get(lsFileName);
        final LineContext context = new LineContext();
        context.sourceFileSize = lsFile.toFile().length();
        context.started = System.currentTimeMillis();
        context.storage = storage;
        context.lsFileName = lsFileName;
        context.indexProcessId = indexProcessCreate("IMPORT", "FilesImport-"+storage+"-"+lsFile.getFileName());
        context.indexProcessId = context.indexProcessId.replaceAll(".*\"id\":\"([0-9a-h]+)\".*", "$1");

        String fileErrorName = getCurrentTimeStamp();
        Path errorFile = null;
        Path logFile = null;
        try {
            Path root = Paths.get(IndexingAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            errorFile = lsFile.resolveSibling(lsFile.getFileName()+"_"+fileErrorName+".err");
            logFile = lsFile.resolveSibling(lsFile.getFileName()+"_"+fileErrorName + ".log");
            context.fileDone = lsFile.resolveSibling(lsFile.getFileName()+".done");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if (context.fileDone.toFile().exists()) {
            log("DONE file found, continue parsing: %s", context.fileDone.getFileName());
            List<String> doneText = Files.readAllLines(context.fileDone);
            context.skipLines = Long.parseLong(doneText.get(0));
            log("   starting line %d >> %s", context.skipLines, doneText.get(1));
        }
        try (BufferedWriter writerError = Files.newBufferedWriter(errorFile);
             BufferedWriter writerLog = Files.newBufferedWriter(logFile)){
            context.writerError = writerError;
            context.writerLog = writerLog;
/* LS file sample:

./_BACKUP:
total 2756045
drwxr-xr-x   16 gorbush  staff         18 2015-01-11 14:20:33 _Backup_Hero/
-rwxr-xr-x   1 gorbush  staff         0 2014-08-13 14:30:30 adb sample file*
*/
            log("Processing LS file %s", lsFile.toAbsolutePath().toString());
            log("          log file %s", logFile.toAbsolutePath().toString());
            log("        error file %s", errorFile.toAbsolutePath().toString());

            LineConsumer consumer = new LineConsumer(context);
            indexProcessStatus(context.indexProcessId, "RUNNING");
            java.nio.file.Files.lines(lsFile, Charset.forName("UTF8")).forEach(consumer);

            String message = String.format("Statistics:\n  lines   : %6d\n   folders: %6d\n   sent   : %6d\n" +
                            "    SKIP : %6d\n    OK   : %6d\n    EXIST: %6d\n    FAIL : %6d\n    BAD  : %6d",
                    context.lineIndex.get(), context.foldersProcessed.get(), context.linesSent.get(),
                    context.linesSkipped.get(), context.putOk.get(), context.putExists.get(), context.putFail.get(), context.putBad.get());
            log(message);
            consumer.writeDoneFile(context.lineIndex.get(), context.sourceFileSize, message);
            indexProcessStatus(context.indexProcessId, "FINISHED");
        } catch (Exception e) {
            indexProcessStatus(context.indexProcessId, "FAILED");
            logError("Failed processing of file %s on line %5d. Reason: %s", lsFileName, context.lineIndex.get(), e.getMessage());
            e.printStackTrace();
        } finally {
            long ended = System.currentTimeMillis();
            log("  Processing took %s", millisToIntervalSeconds(ended-context.started));
        }
    }

    private String indexProcessStatus(String processId, String status) throws IOException {
        URL url = new URL(server+CONTROLLER_PROCESSES+"/status/"+processId+"/"+status);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.setRequestMethod("PUT");
        http.setDoInput(true);
        int code;
        String resultText = "";
        try {
            http.connect();
            code = http.getResponseCode();
            resultText = readFullyAsString(http.getInputStream(), "UTF-8");
        } catch (ConnectException e) {
            return IDResult.NOT_ACCESSIBLE;
        } catch (Exception e) {
            logError("Filed to put the file into dictionary %d", e.getMessage());
            return IDResult.FAILED;
        } finally {
            http.disconnect();
        }

        if (code == HttpURLConnection.HTTP_OK) {
            return resultText;
        }
        if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
            return IDResult.BAD_REQUEST;
        }
        log("Code is %d", code);
        return IDResult.FAILED;
    }

    public String readFullyAsString(InputStream inputStream, String encoding) throws IOException {
        return readFully(inputStream).toString(encoding);
    }

    private ByteArrayOutputStream readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos;
    }

    private String indexProcessCreate(String type, String name) throws IOException {
        URL url = new URL(server+CONTROLLER_PROCESSES+"/"+type+"/"+name);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.setRequestMethod("PUT");
        http.setDoInput(true);
        int code;
        String resultText = "";
        try {
            http.connect();
            code = http.getResponseCode();
            resultText = readFullyAsString(http.getInputStream(), "UTF-8");
        } catch (ConnectException e) {
            return IDResult.NOT_ACCESSIBLE;
        } catch (Exception e) {
            logError("Filed to put the file into dictionary %d", e.getMessage());
            return IDResult.FAILED;
        } finally {
            http.disconnect();
        }

        if (code == HttpURLConnection.HTTP_OK) {
            return resultText;
        }
        if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
            return IDResult.BAD_REQUEST;
        }
        log("Code is %d", code);
        return IDResult.FAILED;
    }

    public static void main(String[] args) throws IOException {
        long started = System.currentTimeMillis();
        try {
            IndexingAgent agent = new IndexingAgent();

//            agent.processLSFile("NAS:Trash", "/Users/spuliaiev/work_mine/PictureGallery/Trash-2018-06-09__sh.log");

            // "NAS:Trash"
            if (args.length == 0) {
                logError("Please, provide the storage name as first argument");
                exit(1);
            }
            String storage = args[0];
            String fileName = args.length > 1 ? args[1] : null;
//
            if (fileName != null) {
                File file = new File(fileName);
                if (file.exists()) {
                    if (file.isFile()) {
                        agent.processLSFile(storage, fileName);
                    } else {
                        agent.processFolder(storage, fileName);
                    }
                } else {
                    logError("File or folder not found %s", fileName);
                }
            } else {
                agent.processFolder(storage, ".");
            }

        } finally {
            long ended = System.currentTimeMillis();
            log("Processing in total took %5s", millisToIntervalSeconds(ended-started));
        }
    }

    private static void processFolder(String storage, Path dir) throws IOException {
        long started = System.currentTimeMillis();
        try {
            // files processing
            log("Processing folder %s", dir.toAbsolutePath().toString());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, file -> file.toFile().isFile())) {
                for (Path entry: stream) {
                    callPutFile(storage, 6594, "2014-03-14T17:53:38", entry.toFile().getPath(), entry.toFile().getName());
                }
            }
            // subfolders processing
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, file -> file.toFile().isDirectory())) {
                for (Path entry: stream) {
                    processFolder(storage, entry);
                }
            }
        } finally {
            long ended = System.currentTimeMillis();
            log("Processing took %s", millisToIntervalSeconds(ended-started));
        }
    }

    private static FilePutResult callPutFile(String storage, long fileSize, String fileStamp, String filePath, String fileName) throws IOException {
        String content = "";
        String fullPath = (filePath.length() > 0 ? (filePath + "/") : "") + fileName;

        String encodedFileName = URLEncoder.encode(fullPath, "UTF8").replaceAll("%2F", "/");
        URL url = new URL(server + CONTROLLER_FILES + storage+"/"+fileSize+"/"+fileStamp+"/"+encodedFileName);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.setRequestMethod("PUT");
        int code;
        try {
            http.connect();
            code = http.getResponseCode();
        } catch (ConnectException e) {
            return FilePutResult.NOT_ACCESSIBLE;
        } catch (Exception e) {
            logError("Filed to put the file into dictionary %d", e.getMessage());
            return FilePutResult.FAILED;
        } finally {
            http.disconnect();
        }

        if (code == HttpURLConnection.HTTP_SEE_OTHER) {
            return FilePutResult.ALREADY_EXISTS;
        }
        if (code == HttpURLConnection.HTTP_OK) {
            return FilePutResult.OK;
        }
        if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
            return FilePutResult.BAD_REQUEST;
        }
        log("Code is %d", code);
        return FilePutResult.FAILED;
    }

    private static FilePutResult callPostFile(String indexProcessId, String storage, long fileSize, String fileStamp, String filePath, String fileName) throws IOException {
        String content = "{ " +
                "\"indexProcessId\": \""+indexProcessId+"\","+
                "\"storage\": \""+escapeJSon(storage)+"\","+
                "\"exists\": true,"+
                "\"size\": "+fileSize+","+
                "\"timestamp\": \""+fileStamp+"\","+
                "\"filePath\": \""+escapeJSon(filePath)+"\","+
                "\"fileName\": \""+escapeJSon(fileName)+"\""+
                "}";
        byte[] contentBytes = content.getBytes();
        long contentLength = contentBytes.length;
        URL url = new URL(server + CONTROLLER_FILES + "put");
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.setFixedLengthStreamingMode(contentLength);
        http.setRequestProperty( "Content-Length", String.valueOf(contentLength));
        int code;
        try {
            http.connect();
            try(OutputStream os = http.getOutputStream()) {
                os.write(contentBytes);
            }
            code = http.getResponseCode();
        } catch (ConnectException e) {
            return FilePutResult.NOT_ACCESSIBLE;
        } catch (Exception e) {
            logError("Filed to put the file into dictionary %d", e.getMessage());
            return FilePutResult.FAILED;
        } finally {
            http.disconnect();
        }

        if (code == HttpURLConnection.HTTP_SEE_OTHER) {
            return FilePutResult.ALREADY_EXISTS;
        }
        if (code == HttpURLConnection.HTTP_OK) {
            return FilePutResult.OK;
        }
        if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
            return FilePutResult.BAD_REQUEST;
        }
        log("Code is %d", code);
        return FilePutResult.FAILED;
    }

    private static String escapeJSon(String text) {
        return text == null ? null :
                text
                .replaceAll("[\\\\]", "\\\\\\\\")
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\b", "\\\\b")
                .replaceAll("\n", "\\\\n")
                .replaceAll("\r", "\\\\r")
                .replaceAll("\t", "\\\\t")
                .replaceAll("\f", "\\\\f")
                ;
    }


}
