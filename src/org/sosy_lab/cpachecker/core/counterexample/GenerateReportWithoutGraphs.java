package org.sosy_lab.cpachecker.core.counterexample;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.logging.Level.WARNING;
import static org.sosy_lab.common.io.PathTemplate.ofFormatString;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import org.sosy_lab.common.JSON;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.export.DOTBuilder2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

@Options
public class GenerateReportWithoutGraphs {

  private static final Splitter LINE_SPLITTER = Splitter.on('\n');
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults();

  private static final Path SCRIPTS = Paths.get("scripts");
  private static final Path REPORT = Paths.get("report");
  private static final Path HTML_TEMPLATE = SCRIPTS.resolve("report_template.html");
  private static final PathTemplate OUT_HTML = ofFormatString("report_withoutGraphs_%d.html");
  private static final Path NO_PATHS_OUT_HTML = Paths.get("report_withoutGraphs.html");

  private final Configuration config;
  private final LogManager logger;
  private final CFA cfa;
  private final DOTBuilder2 dotBuilder;

  @Option(
    secure = true,
    name = "analysis.programNames",
    description = "A String, denoting the programs to be analyzed"
  )
  private String programs;

  @Option(secure = true, name = "log.file", description = "name of the log file")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path logFile = Paths.get("CPALog.txt");

  @Option(secure = true, name = "statistics.file", description = "write some statistics to disk")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path statisticsFile = Paths.get("Statistics.txt");

  @Nullable private final Path outputPath;
  @Nullable private final Path reportDir;

  private final List<Path> errorPathFiles = new ArrayList<>();
  private final List<String> sourceFiles = new ArrayList<>();

  public GenerateReportWithoutGraphs(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    config = checkNotNull(pConfig);
    logger = checkNotNull(pLogger);
    cfa = checkNotNull(pCfa);
    dotBuilder = new DOTBuilder2(pCfa);

    config.inject(this);
    if (statisticsFile != null) {
      outputPath = statisticsFile.getParent();
    } else if (logFile != null) {
      outputPath = logFile.getParent();
    } else {
      outputPath = null;
    }

    if (outputPath != null) {
      reportDir = outputPath.resolve(REPORT);

      sourceFiles.addAll(COMMA_SPLITTER.splitToList(programs));
      errorPathFiles.addAll(getErrorPathFiles());

    } else {
      reportDir = null;
    }
  }

  private List<Path> getErrorPathFiles() {
    List<Path> errorPaths = Lists.newArrayList();
    PathTemplate errorPathTemplate = PathTemplate.ofFormatString(outputPath + "/ErrorPath.%d.json");

    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      Path errorPath = errorPathTemplate.getPath(i);
      if (errorPath.exists()) {
        errorPaths.add(errorPath);

      } else {
        break;
      }
    }

    return errorPaths;
  }

  public void generate() {
    if (outputPath == null) {
      return; // output is disabled
    }

    if (errorPathFiles.isEmpty()) {
      fillOutTemplate(-1);

    } else {
      for (int i = 0; i < errorPathFiles.size(); i++) {
        fillOutTemplate(i);
      }
    }
  }

  private void fillOutTemplate(int round) {
    final Path outFileName;
    if (round == -1) {
      outFileName = NO_PATHS_OUT_HTML;
    } else {
      outFileName = OUT_HTML.getPath(round);
    }

    Path reportPath = reportDir.resolve(outFileName);
    try {
      Files.createParentDirs(reportPath);
    } catch (IOException e) {
      logger.logUserException(WARNING, e, "Could not create report.");
      return;
    }

    try (BufferedReader template =
            new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(HTML_TEMPLATE.toFile()), Charset.defaultCharset()));
        BufferedWriter report =
            new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(reportPath.toFile()), Charset.defaultCharset()))) {

      String line;
      while (null != (line = template.readLine())) {
        if (line.contains("CONFIGURATION")) {
          insertConfiguration(report);
        } else if (line.contains("STATISTICS")) {
          insertStatistics(report);
        } else if (line.contains("SOURCE_CONTENT")) {
          insertSources(report);
        } else if (line.contains("LOG")) {
          insertLog(report);
        } else if (line.contains("ERRORPATH") && round != -1) {
          insertErrorPathData(errorPathFiles.get(round), report);
        } else if (line.contains("FUNCTIONS")) {
          insertFunctionNames(report);
        } else if (line.contains("SOURCE_FILE_NAMES")) {
          insertSourceFileNames(report);
        } else if (line.contains("COMBINEDNODES")) {
          insertCombinedNodesData(report);
        } else if (line.contains("CFAINFO")) {
          insertCfaInfoData(report);
        } else if (line.contains("FCALLEDGES")) {
          insertFCallEdges(report);
        } else {
          report.write(line + "\n");
        }
      }

    } catch (IOException e) {
      logger.logUserException(
          WARNING, e, "Could not create report: Procesing of HTML template failed.");
    }
  }

  private void insertStatistics(BufferedWriter bufferedWriter) throws IOException {
    if (statisticsFile.exists() && statisticsFile.isFile()) {
      int iterator = 0;
      try (BufferedReader statistics =
          new BufferedReader(
              new InputStreamReader(
                  new FileInputStream(statisticsFile.toFile()), Charset.defaultCharset()))) {

        String line;
        while (null != (line = statistics.readLine())) {
          line = "<pre id=\"statistics-" + iterator + "\">" + line + "</pre>\n";
          bufferedWriter.write(line);
          iterator++;
        }

      } catch (IOException e) {
        logger.logUserException(
            WARNING, e, "Could not create report: Writing of statistics failed.");
      }

    } else {
      bufferedWriter.write("<p>No Statistics-File available</p>");
    }
  }

  private void insertSources(Writer report) throws IOException {
    int index = 0;
    for (String sourceFile : sourceFiles) {
      insertSource(Paths.get(sourceFile), report, index);
      index++;
    }
  }

  private void insertSource(Path sourcePath, Writer report, int sourceFileNumber)
      throws IOException {

    if (sourcePath.exists()) {

      int iterator = 0;
      try (BufferedReader source =
          new BufferedReader(
              new InputStreamReader(
                  new FileInputStream(sourcePath.toFile()), Charset.defaultCharset()))) {

        report.write(
            "<table class=\"sourceContent\" ng-show = \"sourceFileIsSet("
                + sourceFileNumber
                + ")\">\n");

        String line;
        while (null != (line = source.readLine())) {
          line = "<td><pre class=\"prettyprint\">" + line + "  </pre></td>";
          report.write(
              "<tr id=\"source-"
                  + iterator
                  + "\"><td><pre>"
                  + iterator
                  + "</pre></td>"
                  + line
                  + "</tr>\n");
          iterator++;
        }

        report.write("</table>\n");

      } catch (IOException e) {
        logger.logUserException(
            WARNING, e, "Could not create report: Inserting source code failed.");
      }

    } else {
      report.write("<p>No Source-File available</p>");
    }
  }


  private void insertConfiguration(Writer report) throws IOException {

    Iterable<String> lines = LINE_SPLITTER.split(config.asPropertiesString());

    int iterator = 0;
    for (String line : lines) {
      line = "<pre id=\"config-" + iterator + "\">" + line + "</pre>\n";
      report.write(line);
      iterator++;
    }
  }

  private void insertLog(Writer bufferedWriter) throws IOException {
    if (logFile != null && logFile.exists()) {
      try (BufferedReader log =
          new BufferedReader(
              new InputStreamReader(
                  new FileInputStream(logFile.toFile()), Charset.defaultCharset()))) {

        int iterator = 0;
        String line;
        while (null != (line = log.readLine())) {
          line = "<pre id=\"log-" + iterator + "\">" + line + "</pre>\n";
          bufferedWriter.write(line);
          iterator++;
        }

      } catch (IOException e) {
        logger.logUserException(WARNING, e, "Could not create report: Adding log failed.");
      }

    } else {
      bufferedWriter.write("<p>No Log-File available</p>");
    }
  }

  private void insertFCallEdges(BufferedWriter report) throws IOException {
    report.write("var fCallEdges = ");
    dotBuilder.writeFunctionCallEdges(report);
    report.write(";\n");
  }

  private void insertCombinedNodesData(Writer report) throws IOException {
    report.write("var combinedNodes = ");
    dotBuilder.writeCombinedNodes(report);
    report.write(";\n");
  }

  private void insertCfaInfoData(Writer report) throws IOException {
    report.write("var cfaInfo = ");
    dotBuilder.writeCfaInfo(report);
    report.write(";\n");
  }

  private void insertErrorPathData(Path errorPatData, BufferedWriter bufferedWriter) {
    if (errorPatData.exists()) {
      try (BufferedReader bufferedReader =
          new BufferedReader(
              new InputStreamReader(
                  new FileInputStream(errorPatData.toFile()), Charset.defaultCharset()))) {

        String line;
        bufferedWriter.write("var errorPathData = ");
        while (null != (line = bufferedReader.readLine())) {
          bufferedWriter.write(line);
        }
        bufferedWriter.write(";\n");

      } catch (IOException e) {
        logger.logUserException(
            WARNING, e, "Could not create report: Insertion of error path data failed.");
      }
    }
  }

  private void insertFunctionNames(BufferedWriter report) {
    try {
      report.write("var functions = ");
      JSON.writeJSONString(cfa.getAllFunctionNames(), report);
      report.write(";\n");

    } catch (IOException e) {
      logger.logUserException(
          WARNING, e, "Could not create report: Insertion of function names failed.");
    }
  }

  private void insertSourceFileNames(BufferedWriter report) {
    try{
      report.write("var sourceFiles = ");
      JSON.writeJSONString(sourceFiles, report);
      report.write(";\n");

    } catch (IOException e) {
      logger.logUserException(
          WARNING, e, "Could not create report: Insertion of source file names failed.");
    }
  }
}
