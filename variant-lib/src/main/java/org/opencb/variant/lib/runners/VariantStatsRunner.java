package org.opencb.variant.lib.runners;

import org.opencb.javalibs.bioformats.commons.filters.FilterApplicator;
import org.opencb.javalibs.bioformats.pedigree.Pedigree;
import org.opencb.javalibs.bioformats.pedigree.io.readers.PedDataReader;
import org.opencb.javalibs.bioformats.pedigree.io.readers.PedFileDataReader;
import org.opencb.javalibs.bioformats.pedigree.io.writers.PedDataWriter;
import org.opencb.javalibs.bioformats.variant.vcf4.VariantEffect;
import org.opencb.javalibs.bioformats.variant.vcf4.VcfRecord;
import org.opencb.javalibs.bioformats.variant.vcf4.filters.VcfFilter;
import org.opencb.javalibs.bioformats.variant.vcf4.io.readers.VariantDataReader;
import org.opencb.javalibs.bioformats.variant.vcf4.io.readers.VariantVcfDataReader;
import org.opencb.javalibs.bioformats.variant.vcf4.io.writers.stats.VariantStatsDataWriter;
import org.opencb.javalibs.bioformats.variant.vcf4.io.writers.stats.VariantStatsSqliteDataWriter;
import org.opencb.javalibs.bioformats.variant.vcf4.stats.*;
import org.opencb.variant.lib.effect.EffectCalculator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: aaleman
 * Date: 9/2/13
 * Time: 6:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class VariantStatsRunner {

    private List<VcfFilter> filters;
    private int numThreads;
    private VariantDataReader vcfReader;
    private VariantStatsDataWriter vcfWriter;
    private PedDataReader pedReader;
    private PedDataWriter pedWriter;
    private boolean effect;
    private boolean stats;


    public VariantStatsRunner() {
        this.filters = null;
        this.numThreads = 1;
        this.stats = true;
        this.effect = true;
    }

    public VariantStatsRunner(String vcfFilePath, String sqliteFileName, String pedFilePath) {
        this();
        vcfReader = new VariantVcfDataReader(vcfFilePath);
        vcfWriter = new VariantStatsSqliteDataWriter(sqliteFileName);

        if (pedFilePath != null) {
            pedReader = new PedFileDataReader(pedFilePath);
        }

    }

    public VariantStatsRunner stats() {
        this.stats = true;
        return this;
    }

    public VariantStatsRunner effect() {
        this.effect = true;
        return this;
    }

    public VariantStatsRunner reader(VariantDataReader reader) {
        this.vcfReader = reader;
        return this;
    }

    public VariantStatsRunner writer(VariantStatsDataWriter writer) {
        this.vcfWriter = writer;
        return this;
    }


    public VariantStatsRunner(VariantDataReader vcfReader, VariantStatsDataWriter vcfWriter) {
        this();
        this.vcfReader = vcfReader;
        this.vcfWriter = vcfWriter;
    }

    public VariantStatsRunner filter(List<VcfFilter> filterList) {
        this.filters = filterList;
        return this;
    }


    public VariantStatsRunner parallel(int numThreads) {
        this.numThreads = numThreads;
        return this;
    }

    public void run() throws IOException {
        int batchSize = 1000;

        Pedigree ped = null;

        VcfGlobalStat globalStat;
        VcfSampleStat vcfSampleStat;

        List<VcfRecord> batch;
        List<VariantEffect> batchEffect;
        List<VcfVariantStat> statsList;
        List<VcfGlobalStat> globalStats = new ArrayList<>(100);
        List<VcfSampleStat> sampleStats = new ArrayList<>(100);
        List<VcfSampleGroupStat> sampleGroupPhen = new ArrayList<>(100);
        List<VcfSampleGroupStat> sampleGroupFam = new ArrayList<>(100);

        if (pedReader != null) {
            pedReader.open();
            ped = pedReader.read();
            pedReader.close();
        }


        vcfReader.open();
        vcfWriter.open();

        vcfReader.pre();
        vcfWriter.pre();


        VcfSampleGroupStat vcfSampleGroupStatPhen;
        VcfSampleGroupStat vcfSampleGroupStatFam;


        VcfVariantGroupStat groupStatsBatchPhen = null;
        VcfVariantGroupStat groupStatsBatchFam = null;

        batch = vcfReader.read(batchSize);

        while (!batch.isEmpty()) {

            if (filters != null) {
                batch = FilterApplicator.filter(batch, filters);
            }

            if (stats) {
                statsList = CalculateStats.variantStats(batch, vcfReader.getSampleNames(), ped);
                globalStat = CalculateStats.globalStats(statsList);
                globalStats.add(globalStat);

                vcfSampleStat = CalculateStats.sampleStats(batch, vcfReader.getSampleNames(), ped);
                sampleStats.add(vcfSampleStat);

                if (ped != null) {
                    groupStatsBatchPhen = CalculateStats.groupStats(batch, ped, "phenotype");
                    groupStatsBatchFam = CalculateStats.groupStats(batch, ped, "family");

                    vcfSampleGroupStatPhen = CalculateStats.sampleGroupStats(batch, ped, "phenotype");
                    sampleGroupPhen.add(vcfSampleGroupStatPhen);

                    vcfSampleGroupStatFam = CalculateStats.sampleGroupStats(batch, ped, "family");
                    sampleGroupFam.add(vcfSampleGroupStatFam);

                }

                vcfWriter.writeVariantStats(statsList);
                vcfWriter.writeVariantGroupStats(groupStatsBatchPhen);
                vcfWriter.writeVariantGroupStats(groupStatsBatchFam);
            }

            if (effect) {


                batchEffect = EffectCalculator.variantEffects(batch);
                vcfWriter.writeVariantEffect(batchEffect);

            }

            batch = vcfReader.read(batchSize);
        }

        globalStat = new VcfGlobalStat(globalStats);
        vcfSampleStat = new VcfSampleStat(vcfReader.getSampleNames(), sampleStats);
        vcfSampleGroupStatPhen = new VcfSampleGroupStat(sampleGroupPhen);
        vcfSampleGroupStatFam = new VcfSampleGroupStat(sampleGroupFam);

        vcfWriter.writeGlobalStats(globalStat);
        vcfWriter.writeSampleStats(vcfSampleStat);

        vcfWriter.writeSampleGroupStats(vcfSampleGroupStatFam);
        vcfWriter.writeSampleGroupStats(vcfSampleGroupStatPhen);

        vcfReader.post();
        vcfWriter.post();

        vcfReader.close();
        vcfWriter.close();
    }

    public boolean isEffect() {
        return effect;
    }

    public void setEffect(boolean effect) {
        this.effect = effect;
    }
}
