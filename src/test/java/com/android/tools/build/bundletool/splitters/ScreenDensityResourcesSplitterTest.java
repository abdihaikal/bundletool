/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.splitters.ScreenDensityResourcesSplitter.DEFAULT_DENSITY_BUCKETS;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TVDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XXHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.XXXHDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory._560DPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.forDpi;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assertForNonDefaultSplits;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assertForSingleDefaultSplit;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.DEFAULT_DENSITY_VALUE;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.HDPI_VALUE;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.XXXHDPI_VALUE;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static junit.framework.TestCase.fail;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.StringPool;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourcesTableFactory;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.android.tools.build.bundletool.version.Version;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth8;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScreenDensityResourcesSplitterTest {

  private final ScreenDensityResourcesSplitter splitter =
      new ScreenDensityResourcesSplitter(BundleToolVersion.getCurrentVersion());

  @Test
  public void noResourceTable_noResourceSplits() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86/liba.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    assertThat(splitter.split(ModuleSplit.forResources(testModule)))
        .containsExactly(ModuleSplit.forResources(testModule));
  }

  @Test
  public void noDensityResources_noDensitySplits() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable/test.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "test",
                                fileReference(
                                    "res/drawable/test.jpg",
                                    Configuration.getDefaultInstance()))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit resourcesModule = ModuleSplit.forResources(testModule);

    ImmutableCollection<ModuleSplit> splits = splitter.split(resourcesModule);
    assertThat(splits).hasSize(1);

    ModuleSplit baseSplit = splits.iterator().next();
    assertThat(baseSplit.getResourceTable().get()).containsResource("com.test.app:drawable/test");
    assertThat(baseSplit.findEntry("res/drawable/test.jpg")).isPresent();
  }

  @Test
  public void allSplitsPresentWithResourceTable() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .setResourceTable(
                ResourcesTableFactory.createResourceTable(
                    "image",
                    fileReference("res/drawable-mdpi/image.jpg", MDPI),
                    fileReference("res/drawable-hdpi/image.jpg", HDPI)))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ImmutableSet<DensityAlias> densities =
        ImmutableSet.of(
            DensityAlias.LDPI,
            DensityAlias.MDPI,
            DensityAlias.TVDPI,
            DensityAlias.HDPI,
            DensityAlias.XHDPI,
            DensityAlias.XXHDPI,
            DensityAlias.XXXHDPI);
    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));
    for (ModuleSplit resourceSplit : densitySplits) {
      assertThat(resourceSplit.getResourceTable().isPresent()).isTrue();
    }
    List<ApkTargeting> targeting =
        densitySplits.stream().map(split -> split.getApkTargeting()).collect(Collectors.toList());
    assertThat(targeting)
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            ApkTargeting.getDefaultInstance(),
            apkDensityTargeting(
                DensityAlias.LDPI, Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.MDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.HDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.TVDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI))));
  }

  @Test
  public void mipmapsNotIncludedInConfigSplits() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "mipmap",
                    entry(
                        0x01,
                        "launcher_icon",
                        fileReference("res/mipmap-hdpi/launcher_icon.png", HDPI),
                        fileReference(
                            "res/mipmap/launcher_icon.png", Configuration.getDefaultInstance()))),
                type(
                    0x02,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable-hdpi/image.jpg", HDPI),
                        fileReference("res/drawable-xhdpi/image.jpg", XHDPI)))));

    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/mipmap/launcher_icon.png")
            .addFile("res/mipmap-hdpi/launcher_icon.png")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-xhdpi/image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> allSplits =
        splitter.split(ModuleSplit.forResources(testModule));
    assertThat(allSplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);

    assertForSingleDefaultSplit(
        allSplits,
        defaultSplit -> {
          assertThat(defaultSplit.getResourceTable()).isPresent();
          ResourceTable defaultResourceTable = defaultSplit.getResourceTable().get();
          assertThat(defaultResourceTable)
              .containsResource("com.test.app:mipmap/launcher_icon")
              .withConfigSize(2)
              .withDensity(DEFAULT_DENSITY_VALUE)
              .withDensity(HDPI_VALUE);
        });

    assertForNonDefaultSplits(
        allSplits,
        densitySplit -> {
          assertThat(densitySplit.getResourceTable()).isPresent();
          ResourceTable splitResourceTable = densitySplit.getResourceTable().get();
          assertThat(splitResourceTable)
              .doesNotContainResource("com.test.app:mipmap/launcher_icon");
        });
  }

  @Test
  public void preservesSourcePool() throws Exception {
    StringPool sourcePool =
        StringPool.newBuilder().setData(ByteString.copyFrom(new byte[] {'x'})).build();
    ResourceTable table =
        ResourcesTableFactory.createResourceTable(
                "image", fileReference("res/drawable-mdpi/image.jpg", MDPI))
            .toBuilder()
            .setSourcePool(sourcePool)
            .build();
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-mdpi/test.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));
    assertThat(densitySplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);

    for (ModuleSplit densitySplit : densitySplits) {
      assertThat(densitySplit.getResourceTable()).isPresent();
      assertThat(densitySplit.getResourceTable().get().getSourcePool()).isEqualTo(sourcePool);
    }
  }

  @Test
  public void picksTheResourceForExactDensity() throws Exception {
    ResourceTable table =
        ResourcesTableFactory.createResourceTable(
            "image",
            fileReference("res/drawable-ldpi/image.jpg", LDPI),
            fileReference("res/drawable-mdpi/image.jpg", MDPI),
            fileReference("res/drawable-tvdpi/image.jpg", TVDPI),
            fileReference("res/drawable-hdpi/image.jpg", HDPI),
            fileReference("res/drawable-xhdpi/image.jpg", XHDPI),
            fileReference("res/drawable-xxhdpi/image.jpg", XXHDPI),
            fileReference("res/drawable-xxxhdpi/image.jpg", XXXHDPI));

    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-ldpi/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("res/drawable-tvdpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-xhdpi/image.jpg")
            .addFile("res/drawable-xxhdpi/image.jpg")
            .addFile("res/drawable-xxxhdpi/image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableSet<DensityAlias> densities =
        ImmutableSet.of(
            DensityAlias.LDPI,
            DensityAlias.MDPI,
            DensityAlias.TVDPI,
            DensityAlias.HDPI,
            DensityAlias.XHDPI,
            DensityAlias.XXHDPI,
            DensityAlias.XXXHDPI);
    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(testModule));
    assertThat(densitySplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);
    assertThat(
            densitySplits.stream().map(split -> split.getApkTargeting()).collect(toImmutableSet()))
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            ApkTargeting.getDefaultInstance(),
            apkDensityTargeting(
                DensityAlias.LDPI, Sets.difference(densities, ImmutableSet.of(DensityAlias.LDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.MDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.MDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.HDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.HDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.XXXHDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.XXXHDPI))),
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.TVDPI),
                Sets.difference(densities, ImmutableSet.of(DensityAlias.TVDPI))));

    for (ModuleSplit densitySplit : densitySplits) {
      assertThat(densitySplit.getResourceTable().isPresent()).isTrue();
      ResourceTable splitResourceTable = densitySplit.getResourceTable().get();

      // we are not verifying the default split in this test.
      if (densitySplit.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        continue;
      }

      assertThat(densitySplit.getApkTargeting().hasScreenDensityTargeting()).isTrue();
      switch (densitySplit
          .getApkTargeting()
          .getScreenDensityTargeting()
          .getValue(0)
          .getDensityAlias()) {
        case LDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(LDPI);
          break;
        case MDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(MDPI);
          break;
        case TVDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(TVDPI);
          break;
        case HDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(HDPI);
          break;
        case XHDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(XHDPI);
          break;
        case XXHDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(XXHDPI);
          break;
        case XXXHDPI:
          assertThat(splitResourceTable)
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(XXXHDPI);
          break;
        default:
          break;
      }
    }
  }

  @Test
  public void twoDensitiesInSameBucket() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable-xxhdpi/image.png", XXHDPI),
                        fileReference("res/drawable-560dpi/image.png", _560DPI),
                        fileReference("res/drawable-xxxhdpi/image.png", XXXHDPI)))));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-xxhdpi/image.png")
            .addFile("res/drawable-560dpi/image.png")
            .addFile("res/drawable-xxxhdpi/image.png")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(module));

    ModuleSplit xxxhdpiSplit =
        findModuleSplitWithScreenDensityTargeting(
            densitySplits,
            ScreenDensity.newBuilder().setDensityAlias(DensityAlias.XXXHDPI).build());
    assertThat(xxxhdpiSplit.getResourceTable()).isPresent();
    ResourceTable resourceTable = xxxhdpiSplit.getResourceTable().get();

    assertThat(resourceTable)
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(2)
        .withDensity(XXXHDPI_VALUE)
        .withDensity(560);
  }

  /**
   * An edge case where xxxhdpi split capturing devices from 527dpi and above should capture 512dpi
   * resources and above.
   *
   * <p>The 512dpi threshold depends on what other dpi values are available. Here, it's driven by
   * the presence of 560dpi config value. A 527dpi device prefers 512dpi over 560dpi.
   *
   * <p>The 512dpi resource should also be present in the xxhdpi split. It targets all devices up to
   * 526dpi.
   */
  @Test
  public void densityBucket_neighbouringResources_edgeCase() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable-mdpi/image.png", MDPI),
                        fileReference("res/drawable-512dpi/image.png", forDpi(512)),
                        fileReference("res/drawable-560dpi/image.png", _560DPI),
                        fileReference("res/drawable-xxxhdpi/image.png", XXXHDPI)))));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-mdpi/image.png")
            .addFile("res/drawable-512dpi/image.png")
            .addFile("res/drawable-560dpi/image.png")
            .addFile("res/drawable-xxxhdpi/image.png")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(
            ImmutableSet.of(DensityAlias.XXHDPI, DensityAlias.XXXHDPI),
            BundleToolVersion.getCurrentVersion());
    ImmutableCollection<ModuleSplit> densitySplits =
        splitter.split(ModuleSplit.forResources(module));

    ModuleSplit xxxhdpiSplit =
        findModuleSplitWithScreenDensityTargeting(
            densitySplits,
            ScreenDensity.newBuilder().setDensityAlias(DensityAlias.XXXHDPI).build());
    assertThat(xxxhdpiSplit.getResourceTable()).isPresent();
    ResourceTable xxxHdpiResourceTable = xxxhdpiSplit.getResourceTable().get();

    assertThat(xxxHdpiResourceTable)
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(3)
        .withDensity(XXXHDPI_VALUE)
        .withDensity(560)
        .withDensity(512);

    ModuleSplit xxhdpiSplit =
        findModuleSplitWithScreenDensityTargeting(
            densitySplits, ScreenDensity.newBuilder().setDensityAlias(DensityAlias.XXHDPI).build());
    Truth8.assertThat(xxhdpiSplit.getResourceTable()).isPresent();
    ResourceTable xxHdpiResourceTable = xxhdpiSplit.getResourceTable().get();

    assertThat(xxHdpiResourceTable)
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(2)
        .withDensity(MDPI_VALUE)
        .withDensity(512);
  }

  @Test
  public void complexDensitySplit() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "mipmap",
                    entry(
                        0x01,
                        "launcher_icon",
                        fileReference("res/mipmap-hdpi/launcher_icon.png", HDPI),
                        fileReference(
                            "res/mipmap/launcher_icon.png", Configuration.getDefaultInstance()))),
                type(
                    0x02,
                    "drawable",
                    entry(
                        0x01,
                        "title_image",
                        fileReference("res/drawable-hdpi/title_image.jpg", HDPI),
                        fileReference("res/drawable-xhdpi/title_image.jpg", XHDPI)))));
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/mipmap/launcher_icon.png")
            .addFile("res/mipmap-hdpi/launcher_icon.png")
            .addFile("res/drawable-hdpi/title_image.jpg")
            .addFile("res/drawable-xhdpi/title_image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> allSplits =
        splitter.split(ModuleSplit.forResources(testModule));

    assertThat(allSplits).hasSize(DEFAULT_DENSITY_BUCKETS.size() + 1);

    assertForSingleDefaultSplit(
        allSplits,
        defaultSplit -> {
          assertThat(defaultSplit.getResourceTable()).isPresent();
          ResourceTable resourceTable = defaultSplit.getResourceTable().get();
          assertThat(resourceTable)
              .containsResource("com.test.app:mipmap/launcher_icon")
              .withConfigSize(2)
              .withDensity(HDPI_VALUE)
              .withDensity(DEFAULT_DENSITY_VALUE);
          assertThat(resourceTable).doesNotContainResource("com.test.app:drawable/title_image");
        });

    assertForNonDefaultSplits(
        allSplits,
        densitySplit -> {
          assertThat(densitySplit.getResourceTable()).isPresent();
          ResourceTable resourceTable = densitySplit.getResourceTable().get();

          assertThat(resourceTable).hasPackage("com.test.app").withNoType("mipmap");
          assertThat(resourceTable)
              .containsResource("com.test.app:drawable/title_image")
              .withConfigSize(1);
          assertThat(densitySplit.getApkTargeting().hasScreenDensityTargeting()).isTrue();
          switch (densitySplit
              .getApkTargeting()
              .getScreenDensityTargeting()
              .getValue(0)
              .getDensityAlias()) {
            case LDPI:
            case MDPI:
            case TVDPI:
            case HDPI:
              assertThat(resourceTable)
                  .containsResource("com.test.app:drawable/title_image")
                  .onlyWithConfigs(HDPI);
              break;
            case XHDPI:
            case XXHDPI:
            case XXXHDPI:
              assertThat(resourceTable)
                  .containsResource("com.test.app:drawable/title_image")
                  .onlyWithConfigs(XHDPI);
              break;
            default:
              fail(String.format("Unexpected targeting: %s", densitySplit.getApkTargeting()));
              break;
          }
        });
  }

  @Test
  public void defaultDensityWithAlternatives() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable/image.jpg", Configuration.getDefaultInstance()),
                        fileReference("res/drawable-hdpi/image.jpg", HDPI)))));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // Master split: Resource not present.
    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .doesNotContainResource("com.test.app:drawable/image");

    // MDPI split: default resource present.
    ModuleSplit mdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.MDPI);
    assertThat(mdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(Configuration.getDefaultInstance());

    // HDPI split: hdpi resource present.
    ModuleSplit hdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.HDPI);
    assertThat(hdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(HDPI);
  }

  /** Before 0.4.0, all default densities ended up in the base regardless of alternatives. */
  @Test
  public void defaultDensityWithAlternatives_before_0_4_0() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference("res/drawable/image.jpg", Configuration.getDefaultInstance()),
                        fileReference("res/drawable-hdpi/image.jpg", HDPI)))));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(Version.of("0.3.3"));
    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // Master split: Resource present with default targeting.
    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(Configuration.getDefaultInstance());

    // MDPI split: hdpi resource present.
    ModuleSplit mdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.MDPI);
    assertThat(mdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(HDPI);

    // HDPI split: hdpi resource present.
    ModuleSplit hdpiSplit = findModuleSplitWithScreenDensityTargeting(splits, DensityAlias.HDPI);
    assertThat(hdpiSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(HDPI);
  }

  @Test
  public void defaultDensityResourceWithoutAlternatives() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "image",
                        fileReference(
                            "res/drawable/image.jpg", Configuration.getDefaultInstance())))));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable/image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // No config split because the resource has no alternatives so ends up in the master split.
    assertThat(splits).hasSize(1);

    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(Configuration.getDefaultInstance());
    assertThat(masterSplit.findEntry("res/drawable/image.jpg")).isPresent();
  }

  @Test
  public void nonDefaultDensityResourceWithoutAlternatives() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(0x01, "image", fileReference("res/drawable-hdpi/image.jpg", HDPI)))));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-hdpi/image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // No config split because the resource has no alternatives so ends up in the master split.
    assertThat(splits).hasSize(1);

    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .onlyWithConfigs(HDPI);
    assertThat(masterSplit.findEntry("res/drawable-hdpi/image.jpg")).isPresent();
  }

  /**
   * Before 0.4.0, non-default density resources without alternatives ended up in config splits
   * instead of in the base split.
   */
  @Test
  public void nonDefaultDensityResourceWithoutAlternatives_before_0_4_0() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(0x01, "image", fileReference("res/drawable-hdpi/image.jpg", HDPI)))));
    BundleModule module =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-hdpi/image.jpg")
            .setResourceTable(table)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ScreenDensityResourcesSplitter splitter =
        new ScreenDensityResourcesSplitter(Version.of("0.3.3"));
    ImmutableCollection<ModuleSplit> splits = splitter.split(ModuleSplit.forResources(module));

    // 1 base + 7 config splits
    assertThat(splits).hasSize(8);

    // The resource is not present in the base.
    ModuleSplit masterSplit = findModuleSplitWithDefaultTargeting(splits);
    assertThat(masterSplit.getResourceTable().get())
        .doesNotContainResource("com.test.app:drawable/image");

    // The resource is present in all config splits.
    assertForNonDefaultSplits(
        splits,
        densitySplit -> {
          assertThat(densitySplit.getResourceTable().get())
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(HDPI);
          assertThat(densitySplit.findEntry("res/drawable-hdpi/image.jpg")).isPresent();
        });
  }

  private static ModuleSplit findModuleSplitWithScreenDensityTargeting(
      ImmutableCollection<ModuleSplit> moduleSplits, DensityAlias densityAlias) {
    return findModuleSplitWithScreenDensityTargeting(
        moduleSplits, ScreenDensity.newBuilder().setDensityAlias(densityAlias).build());
  }

  private static ModuleSplit findModuleSplitWithScreenDensityTargeting(
      ImmutableCollection<ModuleSplit> moduleSplits, ScreenDensity density) {
    return moduleSplits
        .stream()
        .filter(
            split ->
                split.getApkTargeting().getScreenDensityTargeting().getValueCount() > 0
                    && density.equals(
                        split.getApkTargeting().getScreenDensityTargeting().getValue(0)))
        .collect(MoreCollectors.onlyElement());
  }

  private static ModuleSplit findModuleSplitWithDefaultTargeting(
      ImmutableCollection<ModuleSplit> moduleSplits) {
    return moduleSplits
        .stream()
        .filter(split -> split.getApkTargeting().equals(ApkTargeting.getDefaultInstance()))
        .collect(MoreCollectors.onlyElement());
  }
}
