package runnables;

import java.io.File;

public class ListFolder {

    private static String path = "/home/peter.banda/ada-web-0.6.0/lib";

    public static void main(String[] args) {
        System.out.println("Listing " + path);

        File folder = new File(path);
        File[] listedFiles = folder.listFiles();

        if (listedFiles == null) {
            System.out.println("Got an empty response.");
        } else {
            System.out.println("Got " + listedFiles.length + " files.");
        }
    }
}