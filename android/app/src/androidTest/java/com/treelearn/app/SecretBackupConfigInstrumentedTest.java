package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.XmlResourceParser;
import android.security.NetworkSecurityPolicy;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.data.secret.KeystoreSecretStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * T-005 acceptance evidence that the API key cannot leave the device (technical design 14).
 *
 * <p>The claim under test is about the INSTALLED artifact, not about the source file we intended to
 * write. That distinction is deliberate: a manifest edit that never reached the APK, or an
 * attribute that AAPT dropped, would still leave the source looking correct, and T-022/T-004 both
 * carry scars from assertions that certified the wrong artifact. So the manifest is read back out
 * of the installed APK's {@code AndroidManifest.xml} string pool, and the two backup rule resources
 * are parsed from the packaged resources through {@code R.xml}, the same way the platform reads
 * them.
 *
 * <p>What each layer proves, stated so nobody over-reads it:
 * <ul>
 *   <li>{@code FLAG_ALLOW_BACKUP} is cleared: the semantic, runtime-visible fact that Auto Backup
 *       excludes this app.</li>
 *   <li>The string pool declares the four attributes: the packaged manifest really carries them.
 *       This is a declaration check, not a value check.</li>
 *   <li>{@code isCleartextTrafficPermitted()} is false as well, which together with the declaration
 *       rules out {@code android:usesCleartextTraffic="true"} (an explicit "true" is the one thing
 *       that would flip that call back to true while no network security config re-enables
 *       cleartext; that config belongs to T-018).</li>
 *   <li>The parsed rules exclude the secret Preferences file from both the cloud-backup and the
 *       device-transfer section, so a later accidental {@code allowBackup="true"} still cannot
 *       carry the key off the device.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class SecretBackupConfigInstrumentedTest {

    private static final String SECRET_PREFERENCES_FILE = KeystoreSecretStore.PREFERENCES_NAME + ".xml";

    @Test
    public void theInstalledManifestDisablesBackupAndCleartextTraffic() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ApplicationInfo info =
                context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);

        assertEquals("allowBackup must be false: the key, the reasoning and the learning content "
                        + "are all local-only (technical design 14)",
                0, info.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);

        byte[] installedManifest = readApkEntry(new File(info.sourceDir), "AndroidManifest.xml");
        assertNotNull(installedManifest);
        assertTrue("the packaged manifest is empty, so nothing below is proven", installedManifest.length > 0);
        assertFalse("negative control: the string-pool search must not match everything",
                ByteSearch.manifestDeclares(installedManifest, "treelearnAttributeThisAppNeverDeclares"));
        for (String attribute : new String[] {
            "allowBackup", "fullBackupContent", "dataExtractionRules", "usesCleartextTraffic"
        }) {
            assertTrue("the packaged AndroidManifest.xml must declare android:" + attribute,
                    ByteSearch.manifestDeclares(installedManifest, attribute));
        }

        assertFalse("cleartext traffic must not be permitted to the DeepSeek endpoint",
                NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("api.deepseek.com"));
    }

    @Test
    public void everyBackupRuleSetExcludesTheSecretPreferencesFile() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        List<String[]> legacyRules = excludes(context, R.xml.backup_rules);
        List<String[]> extractionRules = excludes(context, R.xml.data_extraction_rules);

        assertTrue("backup_rules.xml parsed into nothing: " + describe(legacyRules), !legacyRules.isEmpty());
        assertTrue("data_extraction_rules.xml parsed into nothing: " + describe(extractionRules),
                !extractionRules.isEmpty());

        assertTrue("R.xml.backup_rules must exclude " + SECRET_PREFERENCES_FILE + " from Auto Backup: "
                        + describe(legacyRules),
                excludes(legacyRules, "", "sharedpref", SECRET_PREFERENCES_FILE));
        assertTrue("R.xml.data_extraction_rules must exclude " + SECRET_PREFERENCES_FILE
                        + " from cloud backup: " + describe(extractionRules),
                excludes(extractionRules, "cloud-backup", "sharedpref", SECRET_PREFERENCES_FILE));
        assertTrue("R.xml.data_extraction_rules must exclude " + SECRET_PREFERENCES_FILE
                        + " from device-to-device transfer: " + describe(extractionRules),
                excludes(extractionRules, "device-transfer", "sharedpref", SECRET_PREFERENCES_FILE));
    }

    private static List<String[]> excludes(Context context, int xmlResourceId) throws Exception {
        List<String[]> entries = new ArrayList<>();
        XmlResourceParser parser = context.getResources().getXml(xmlResourceId);
        String section = "";
        try {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                String tag = parser.getName();
                if ("cloud-backup".equals(tag) || "device-transfer".equals(tag)) {
                    section = tag;
                } else if ("exclude".equals(tag)) {
                    entries.add(new String[] {
                        section,
                        String.valueOf(parser.getAttributeValue(null, "domain")),
                        String.valueOf(parser.getAttributeValue(null, "path")),
                    });
                }
            }
        } finally {
            parser.close();
        }
        return entries;
    }

    private static boolean excludes(List<String[]> entries, String section, String domain, String path) {
        for (String[] entry : entries) {
            if (entry[0].equals(section) && entry[1].equals(domain) && entry[2].equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(List<String[]> entries) {
        StringBuilder rendered = new StringBuilder("[");
        for (String[] entry : entries) {
            if (rendered.length() > 1) {
                rendered.append(", ");
            }
            rendered.append(entry[0]).append('/').append(entry[1]).append('/').append(entry[2]);
        }
        return rendered.append(']').toString();
    }

    private static byte[] readApkEntry(File apk, String name) throws Exception {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry(name);
            assertNotNull("the installed APK has no " + name, entry);
            try (InputStream stream = zip.getInputStream(entry)) {
                ByteArrayOutputStream collected = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    collected.write(buffer, 0, read);
                }
                return collected.toByteArray();
            }
        }
    }
}
