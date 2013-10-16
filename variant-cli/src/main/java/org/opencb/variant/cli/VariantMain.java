package org.opencb.variant.cli;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.cli.*;
import org.opencb.javalibs.bioformats.variant.vcf4.annotators.VcfAnnotator;
import org.opencb.javalibs.bioformats.variant.vcf4.annotators.VcfConsequenceTypeAnnotator;
import org.opencb.javalibs.bioformats.variant.vcf4.annotators.VcfControlAnnotator;
import org.opencb.javalibs.bioformats.variant.vcf4.io.readers.VariantVcfDataReader;
import org.opencb.javalibs.bioformats.variant.vcf4.io.writers.index.VariantIndexSqliteDataWriter;
import org.opencb.javalibs.bioformats.variant.vcf4.io.writers.stats.VariantStatsFileDataWriter;
import org.opencb.variant.cli.servlets.GetFoldersServlet;
import org.opencb.variant.cli.servlets.HelloServlet;
import org.opencb.variant.lib.io.VariantAnnotRunner;
import org.opencb.variant.lib.io.VariantIndexRunner;
import org.opencb.variant.lib.io.VariantStatsRunner;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created with IntelliJ IDEA.
 * User: aaleman
 * Date: 9/5/13
 * Time: 11:33 AM
 * To change this template use File | Settings | File Templates.
 */
public class VariantMain {

    private static Options options;
    private static CommandLine commandLine;
    private static CommandLineParser parser;
    private static HelpFormatter help;

    private Logger logger;

    static {
        parser = new PosixParser();
        help = new HelpFormatter();
    }

    public VariantMain() {
        initOptions();


    }

    private static void initOptions() {
        options = new Options();

        options.addOption(OptionFactory.createOption("help", "h", "Print this message", false, false));
        options.addOption(OptionFactory.createOption("vcf-file", "Input VCF file", true, true));
        options.addOption(OptionFactory.createOption("outdir", "o", "Output dir", true, true));
        options.addOption(OptionFactory.createOption("output-file", "Output filename", false, true));
        options.addOption(OptionFactory.createOption("out-file", "File output", false, false));
        options.addOption(OptionFactory.createOption("ped-file", "Ped file", false, true));
        options.addOption(OptionFactory.createOption("control", "Control filename", false, true));
        options.addOption(OptionFactory.createOption("control-list", "Control filename list", false, true));
        options.addOption(OptionFactory.createOption("control-prefix", "Control prefix", false, true));
        options.addOption(OptionFactory.createOption("threads", "Num threads", false, true));


    }

    public static void main(String[] args) throws IOException, InterruptedException {
        initOptions();

        if (args.length == 0) {
            help.printHelp("variant", options);
            System.exit(-1);

        }
        String command = args[0];

        VariantStatsRunner vr;
        VariantAnnotRunner var;
        VariantIndexRunner vi;
        int numThreads = 1;


        parse(args, false);
        String outputFile;

        if (commandLine.hasOption("threads")) {
            numThreads = Integer.parseInt(commandLine.getOptionValue("threads"));
        }

        switch (command) {
            case "index":
                System.out.println("===== INDEX =====");
//                Runtime r = Runtime.getRuntime();
//                Process p;
//
//                String indexDir = commandLine.getOptionValue("outdir") + "/index";
//                File indexFileDir = new File(indexDir);
//                if (!indexFileDir.exists()) {
//                    indexFileDir.mkdir();
//                }
//
//                String cmd = "python bin/indexerManager.py -t vcf -i " + commandLine.getOptionValue("vcf-file") + " --outdir " + indexDir;
//
//                p = r.exec(cmd);
//                p.waitFor();

                outputFile = "index.db";
                if (commandLine.hasOption("output-file")) {
                    outputFile = commandLine.getOptionValue("output-file");
                }

                vi = new VariantIndexRunner(new VariantVcfDataReader(commandLine.getOptionValue("vcf-file")), new VariantIndexSqliteDataWriter(commandLine.getOptionValue("outdir") + "/" + outputFile));

                vi.run();

                break;

            case "stats":
                System.out.println("===== STATS =====");

                outputFile = "stats.db";

                if (commandLine.hasOption("output-file")) {
                    outputFile = commandLine.getOptionValue("output-file");
                }

                vr = new VariantStatsRunner(commandLine.getOptionValue("vcf-file"), commandLine.getOptionValue("outdir") + "/" + outputFile, commandLine.getOptionValue("ped-file"));

                if (commandLine.hasOption("out-file")) {
                    vr.writer(new VariantStatsFileDataWriter(commandLine.getOptionValue("outdir")));

                }

                vr.run();
                break;

            case "filter":
                System.out.println("===== FILTER =====");
                System.out.println("Under construction");
                break;

            case "test":
                System.out.println("===== TEST =====");
                List<VcfAnnotator> test = new ArrayList<>();
//                test.add(new VcfTestAnnotator());
                test.add(new VcfConsequenceTypeAnnotator());
                var = new VariantAnnotRunner(commandLine.getOptionValue("vcf-file"), commandLine.getOptionValue("outdir") + "/" + "file_annot.vcf");
                var.annotations(test);
                var.parallel(numThreads);
                var.run();


                break;

            case "annot":
                System.out.println("===== ANNOT =====");


                outputFile = "annot.vcf";

                if (commandLine.hasOption("output-file")) {
                    outputFile = commandLine.getOptionValue("output-file");
                }

                List<VcfAnnotator> listAnnots = new ArrayList<>();
                VcfAnnotator control = null;
                String infoPrefix = commandLine.hasOption("control-prefix") ? commandLine.getOptionValue("control-prefix") : "CONTROL";

                var = new VariantAnnotRunner(commandLine.getOptionValue("vcf-file"), commandLine.getOptionValue("outdir") + "/" + outputFile);

                if (commandLine.hasOption("control-list")) {
                    HashMap<String, String> controlList = getControlList(commandLine.getOptionValue("control-list"));
                    control = new VcfControlAnnotator(infoPrefix, controlList);

                } else if (commandLine.hasOption("control")) {
                    control = new VcfControlAnnotator(infoPrefix, commandLine.getOptionValue("control"));

                }

                listAnnots.add(control);
                var.annotations(listAnnots);
                var.parallel(numThreads).run();


                break;

            case "server":
                System.out.println("===== SERVER =====");

                Tomcat tomcat;

                tomcat = new Tomcat();
                tomcat.setPort(31415);

                Context ctx = tomcat.addContext("/variant/rest", new File(".").getAbsolutePath());

                Tomcat.addServlet(ctx, "hello", new HelloServlet());
                ctx.addServletMapping("/hello", "hello");

                Tomcat.addServlet(ctx, "getdirs", new GetFoldersServlet());
                ctx.addServletMapping("/getdirs", "getdirs");


                try {
                    tomcat.start();
                    tomcat.getServer().await();

                } catch (LifecycleException e) {
                    e.printStackTrace();
                }


                break;

            default:
                help.printHelp("variant", options);
                System.exit(-1);
        }
    }

    private static HashMap<String, String> getControlList(String filename) {
        String line;
        HashMap<String, String> map = new LinkedHashMap<>();
        try {

            BufferedReader reader = new BufferedReader(new FileReader(filename));

            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                map.put(fields[0], fields[1]);

            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return map;
    }

    private static boolean checkCommand(String command) {
        return command.equalsIgnoreCase("stats") || command.equalsIgnoreCase("filter") || command.equalsIgnoreCase("index") || command.equalsIgnoreCase("annot") || command.equalsIgnoreCase("test");
    }

    private static void parse(String[] args, boolean stopAtNoOption) {
        parser = new PosixParser();

        try {
            commandLine = parser.parse(options, args, stopAtNoOption);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            help.printHelp("variant", options);
            System.exit(-1);
        }
    }

}
