package net.anzix.flickrsync;

import com.aetrion.flickr.FlickrException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Synchronize a folder structure with flickr.
 */
public class App {

    public static final String FLICKR_PHOTOS = ".flickr.photos";
    public static final String FLICKR_SET = ".flickr.set";
    @Argument(required = true, usage = "directory to flickrsync")
    private String dir;

    @Option(name = "-v", aliases = "--verbose", usage = "verbosed output")
    private boolean verbose = false;

    @Option(name = "-t", aliases = "--auth-token", usage = "Oauth secret token")
    private String token;

    @Option(name = "-n", aliases = "--dry-run", usage = "Simulate only")
    private boolean simulate = false;

    private Worker worker;

    private FilenameFilter imageFilter = new FilenameFilter() {

        @Override
        public boolean accept(File arg0, String arg1) {
            return arg1.toLowerCase().endsWith("jpg");
        }
    };
    private FileFilter dirFilter = new FileFilter() {

        @Override
        public boolean accept(File arg0) {
            return arg0.isDirectory();
        }
    };


    public static void main(String[] args) throws Exception {
        App app = new App();
        CmdLineParser parser = new CmdLineParser(app);
        try {
            parser.parseArgument(args);
            app.start();
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }
    }

    private void start() throws Exception {
        if (simulate) {
            worker = new SimulateWorker();
        } else {
            worker = new FlickrWorker(token, verbose);
        }
        worker.init();
        File baseDir = new File(dir);
        upload(baseDir);

    }

    private void upload(File dir) throws Exception {
        if (verbose) {
            System.out.println("Checking " + dir.getAbsolutePath());
        }
        boolean collection = dir.listFiles(dirFilter).length > 0;
        boolean photos = dir.list(imageFilter).length > 0;
        if (collection && photos) {
            for (File d : dir.listFiles(dirFilter)) {
                System.out.println(d);
            }
            throw new AssertionError("Dir can't contain both subfolders and images");
        }
        if (collection) {
            uploadCollectionFolder(dir);
        } else {
            uploadPhotoFolder(dir);
        }

    }

    private void uploadCollectionFolder(File dir) throws Exception {
        for (File subDir : dir.listFiles(dirFilter)) {
            upload(subDir);
        }

    }

    private void uploadPhotoFolder(File dir) throws Exception {
        String setId = readSetId(dir);
        if (setId == null) {
            if (verbose) {
                System.out.println("Set doesn't exist " + dir.getAbsolutePath());
            }
            Map<String, String> mapping = loadPhotoMapping(dir);
            if (mapping.size() > 0) {
                if (verbose) {
                    System.out.println("Recovering set mapping");
                }
                setId = recoverSet(dir);
            }
        } else {
            setId = worker.validateSet(setId);
            if (setId == null) {
                setId = recoverSet(dir);
            }

        }
        if (setId != null) {
            assignSetToTheRightCollection(dir, setId);
        }
        Map<String, String> p = loadPhotoMapping(dir);

        for (File img : dir.listFiles(imageFilter)) {
            if (!p.containsKey(img.getName())) {
                setId = uploadFile(img, setId);
                // assignToSet();
            }
        }

    }

    private String createSet(File dir, String firstPhoto) throws Exception {
        String setId = worker.createSet(dir.getCanonicalFile().getName(), firstPhoto);
        assignSetToTheRightCollection(dir, setId);
        writeSetId(dir, setId);
        return setId;
    }

    private void assignSetToTheRightCollection(File dir, String setId) throws Exception {
        File f = new File(dir.getParentFile(), ".flickr.collection");
        if (f.exists()) {
            String collectionId = new Scanner(new FileInputStream(f)).useDelimiter("\\Z").next().trim();
            worker.assignSetToCollection(setId, dir.getCanonicalFile().getName(), collectionId);
        }

    }

    private String recoverSet(File dir) throws Exception {
        Map<String, String> mapping = loadPhotoMapping(dir);
        if (mapping.size() > 0) {
            String firstKey = mapping.keySet().iterator().next();
            String setId = createSet(dir, mapping.get(firstKey));
            writeSetId(dir, setId);
            for (String key : mapping.keySet()) {
                if (!key.equals(firstKey)) {
                    try {
                        worker.assignPhotoToSet(setId, mapping.get(key));

                    } catch (FlickrException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            return setId;
        }
        return null;
    }

    private String uploadFile(File img, String setId) throws Exception {
        try {
            String id = worker.uploadFile(img);

            Map<String, String> mapping = loadPhotoMapping(img.getParentFile());
            mapping.put(img.getName(), id);
            writePhotoMappingFile(img.getParentFile(), mapping);
            if (setId == null) {
                setId = createSet(img.getParentFile(), id);
                writeSetId(img.getParentFile(), setId);
                assignSetToTheRightCollection(img.getParentFile(), setId);
            } else {
                worker.assignPhotoToSet(setId, id);
            }
            return setId;
        } catch (Exception ex) {
            ex.printStackTrace();
            return setId;
        }

    }

    private void writePhotoMappingFile(File dir, Map<String, String> content) throws Exception {
        if (!simulate) {
            File photoMapFile = new File(dir, FLICKR_PHOTOS);
            FileWriter writer = new FileWriter(photoMapFile);
            for (String key : content.keySet()) {
                writer.write(key + "=" + content.get(key) + "\n");
            }
            writer.close();
        }
    }

    private Map<String, String> loadPhotoMapping(File dir) throws FileNotFoundException {
        Map<String, String> p = new HashMap<String, String>();
        File photoMapFile = new File(dir, FLICKR_PHOTOS);
        if (photoMapFile.exists()) {
            Scanner s = new Scanner(new FileInputStream(photoMapFile)).useDelimiter("\\n");
            while (s.hasNext()) {
                String line = s.next();
                String[] kv = line.trim().split("=");
                if (kv.length != 2) {
                    System.out.println("ignore line" + line);
                } else {
                    p.put(kv[0].trim(), kv[1].trim());
                }

            }
        }
        return p;
    }

    private String readSetId(File dir) throws Exception {
        File setFile = new File(dir, FLICKR_SET);
        if (setFile.exists()) {
            return new Scanner(setFile).useDelimiter("\\Z").next().trim();

        }
        return null;
    }

    private void writeSetId(File dir, String setId) throws Exception {
        if (!simulate) {
            File setFile = new File(dir, FLICKR_SET);
            FileWriter writer = new FileWriter(setFile);
            writer.write(setId);
            writer.close();
        }
    }

}
