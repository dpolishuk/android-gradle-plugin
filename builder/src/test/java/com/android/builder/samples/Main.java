package com.android.builder.samples;

import com.android.builder.AaptOptions;
import com.android.builder.AndroidBuilder;
import com.android.builder.BuildType;
import com.android.builder.BuildTypeHolder;
import com.android.builder.DefaultSdkParser;
import com.android.builder.ProductFlavor;
import com.android.builder.ProductFlavorHolder;
import com.android.utils.StdLogger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Main {

    private static class DefaultBuildTypeHolder extends DefaultPathProvider
            implements BuildTypeHolder {

        private final BuildType mBuildType;

        DefaultBuildTypeHolder(String root, BuildType buildType) {
            super(root + File.separatorChar + "src" + File.separatorChar + buildType.getName());
            mBuildType = buildType;
        }

        @Override
        public BuildType getBuildType() {
            return mBuildType;
        }
    }

    private static class DefaultProductFlavorHolder extends DefaultPathProvider
            implements ProductFlavorHolder {

        private final ProductFlavor mProductFlavor;

        DefaultProductFlavorHolder(String root, ProductFlavor productFlavor) {
            super(root + File.separatorChar + "src" + File.separatorChar + productFlavor.getName());
            mProductFlavor = productFlavor;
        }

        @Override
        public ProductFlavor getProductFlavor() {
            return mProductFlavor;
        }
    }

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

        String sample = args[1];
        String build = sample + File.separator + "build";
        checkFolder(build);

        String gen = build + File.separator + "gen";
        checkFolder(gen);

        String outRes = build + File.separator + "res";
        checkFolder(outRes);

        DefaultProductFlavorHolder mainHolder = new DefaultProductFlavorHolder(sample, mainFlavor);
        builder.setBuildVariant(
                mainHolder,
                new DefaultBuildTypeHolder(sample, debug));
        builder.addProductFlavor(new DefaultProductFlavorHolder(sample, customFlavor));


        String[] lines = new String[] {
                "public final static int A = 1;"
        };
        builder.generateBuildConfig(
                gen,
                Arrays.asList(lines));

        builder.preprocessResources(
                outRes);

        builder.processResources(
                mainHolder.getAndroidManifest().getAbsolutePath(),
                outRes,
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
