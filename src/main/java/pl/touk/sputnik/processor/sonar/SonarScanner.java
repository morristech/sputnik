package pl.touk.sputnik.processor.sonar;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.sonarsource.scanner.api.EmbeddedScanner;
import pl.touk.sputnik.configuration.Configuration;
import pl.touk.sputnik.configuration.GeneralOption;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
@AllArgsConstructor
public class SonarScanner {

    private final List<String> files;
    private final EmbeddedScanner sonarEmbeddedScanner;
    private final Configuration configuration;

    @VisibleForTesting
    static final String OUTPUT_DIR = ".sonar";

    @VisibleForTesting
    static final String OUTPUT_FILE = "sonar-report.json";

    /**
     * Load base sonar configuration files specified by GeneralOption.SONAR_PROPERTIES
     * configuration key
     * @return a Properties instance
     * @throws IOException
     */
    @VisibleForTesting
    Map<String, String> loadBaseProperties() throws IOException {
        final Properties props = new Properties();
        for (final String property: StringUtils.split(configuration.getProperty(GeneralOption.SONAR_PROPERTIES), ',')){
            final File propertyFile = new File(StringUtils.strip(property));
            log.info("Loading {}", propertyFile.getAbsolutePath());
            props.load(new FileInputStream(propertyFile));
        }
        return (Map)props;
    }

    /**
     * Runs Sonar.
     *
     * @throws IOException
     * @return the json file containing the results.
     */
    public File run() throws IOException {
        Map<String, String> props = loadBaseProperties();
        setAdditionalProperties(props);

        sonarEmbeddedScanner.addGlobalProperties(props);

        log.info("Sonar configuration: {}", props.toString());

        sonarEmbeddedScanner.start();
        sonarEmbeddedScanner.execute(new HashMap<String, String>());
        return new File(OUTPUT_DIR, OUTPUT_FILE);
    }

    /**
     * Set additional properties needed for sonar to run
     * @param props a Properties instance
     */
    @VisibleForTesting
    void setAdditionalProperties(Map<String, String> props) {
        props.put(SonarProperties.INCLUDE_FILES, StringUtils.join(files, ", "));
        props.put(SonarProperties.SCM_ENABLED, "false");
        props.put(SonarProperties.SCM_STAT_ENABLED, "false");
        props.put(SonarProperties.ISSUEASSIGN_PLUGIN, "false");
        props.put(SonarProperties.EXPORT_PATH, OUTPUT_FILE);
        props.put(SonarProperties.VERBOSE, configuration.getProperty(GeneralOption.SONAR_VERBOSE));
        props.put(SonarProperties.WORKDIR, OUTPUT_DIR);
        props.put(SonarProperties.PROJECT_BASEDIR, ".");
        props.put(SonarProperties.SOURCES, ".");
    }
}
