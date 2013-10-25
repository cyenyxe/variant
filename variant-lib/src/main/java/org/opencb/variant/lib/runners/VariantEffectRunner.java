package org.opencb.variant.lib.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.multipart.FormDataMultiPart;
import org.opencb.commons.bioformats.variant.vcf4.VariantEffect;
import org.opencb.commons.bioformats.variant.vcf4.VcfRecord;
import org.opencb.commons.bioformats.variant.vcf4.io.readers.VariantDataReader;
import org.opencb.commons.bioformats.variant.vcf4.io.writers.effect.VariantEffectDataWriter;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: aaleman
 * Date: 10/24/13
 * Time: 2:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class VariantEffectRunner extends VariantRunner {
    public VariantEffectRunner(VariantDataReader reader, VariantEffectDataWriter writer) {
        super(reader, writer);
    }

    public VariantEffectRunner(VariantDataReader reader, VariantEffectDataWriter writer, VariantRunner prev) {
        super(reader, writer, prev);
    }

    @Override
    public List<VcfRecord> apply(List<VcfRecord> batch) throws IOException {

        if (writer != null) {
            List<VariantEffect> batchEffect = this.variantEffects(batch);
            ((VariantEffectDataWriter) writer).writeVariantEffect(batchEffect);
        }

        return batch;
    }

    @Override
    public void pre() {
    }

    @Override
    public void post() {
    }

    private List<VariantEffect> variantEffects(List<VcfRecord> batch) {


        ObjectMapper mapper = new ObjectMapper();
        List<VariantEffect> batchEffect = new ArrayList<>();

        StringBuilder chunkVcfRecords = new StringBuilder();

        Client wsRestClient = Client.create();
        WebResource webResource = wsRestClient.resource("http://ws.bioinfo.cipf.es/cellbase/rest/latest/hsa/genomic/variant/");

        for (VcfRecord record : batch) {
            chunkVcfRecords.append(record.getChromosome()).append(":");
            chunkVcfRecords.append(record.getPosition()).append(":");
            chunkVcfRecords.append(record.getReference()).append(":");
            chunkVcfRecords.append(record.getAlternate()).append(",");

        }

        FormDataMultiPart formDataMultiPart = new FormDataMultiPart();
        formDataMultiPart.field("variants", chunkVcfRecords.substring(0, chunkVcfRecords.length() - 1));

        String response = webResource.path("consequence_type").queryParam("of", "json").type(MediaType.MULTIPART_FORM_DATA).post(String.class, formDataMultiPart);

        try {
            batchEffect = mapper.readValue(response, mapper.getTypeFactory().constructCollectionType(List.class, VariantEffect.class));
        } catch (IOException e) {
            System.err.println(chunkVcfRecords.toString());
            e.printStackTrace();
        }


        return batchEffect;
    }
}
