package com.android.builder.samples;

import com.android.builder.AaptOptions;
import com.android.builder.AndroidBuilder;
import com.android.builder.BuildType;
import com.android.builder.DefaultSdkParser;
import com.android.builder.ProductFlavor;
import com.android.utils.StdLogger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Main {

    /**
     * Usage: <sdklocation> <samplelocation>
     *
     *
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {

        DefaultSdkParser parser = new DefaultSdkParser(args[0]);

        AndroidBuilder builder = new AndroidBuilder(parser,
                new StdLogger(StdLogger.Level.VERBOSE), true);
        builder.setTarget("android-15");

        ProductFlavor mainFlavor = new ProductFlavor("main");
        ProductFlavor customFlavor = new ProductFlavor("custom");
        BuildType debug = new BuildType(BuildType.DEBUG);

        customFlavor.setMinSdkVersion(15);
        customFlavor.setTargetSdkVersion(16);

        AaptOptions aaptOptions = new AaptOptions();

        builder.setBuildVariant(mainFlavor, customFlavor, debug);

        String sample = args[1];
        String build = sample + File.separator + "build";
        checkFolder(build);

        String gen = build + File.separator + "gen";
        checkFolder(gen);

        String outRes = build + File.separator + "res";
        checkFolder(outRes);

        String[] lines = new String[] {
                "public final static int A = 1;"
        };
        builder.generateBuildConfig(
                sample + File.separator + "AndroidManifest.xml",
                gen,
                Arrays.asList(lines));

        builder.preprocessResources(
                sample + File.separator + "res",
                null, /*flavorResLocation*/
                null, /*typeResLocation*/
                outRes);

        builder.processResources(
                sample + File.separator + "AndroidManifest.xml",
                sample + File.separator + "res",
                null, /*flavorResLocation*/
                null, /*typeResLocation*/
                outRes,
                sample + File.separator + "assets",
                null, /*flavorAssetsLocation*/
                null, /*typeAssetsLocation*/
                gen,
                build + File.separator + "foo.apk_",
                build + File.separator + "foo.proguard.txt",
                aaptOptions);
    }

    private static void checkFolder(String path) {
        File folder = new File(path);
        if (folder.exists() == false) {
            folder.mkdirs();
        }
    }

}
