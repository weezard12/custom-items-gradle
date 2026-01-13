package nl.knokko.customitems.plugin.resourcepack;

import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.durability.ItemDurabilityAssignments;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.ValidationException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static nl.knokko.customitems.MCVersions.*;

public class YamlResourcepackGenerator {

    private YamlResourcepackGenerator() {}

    public static void write(ItemSet itemSet, File outputFile)
            throws IOException, ValidationException, ProgrammingValidationException {
        Map<KciItemType, ItemDurabilityAssignments> assignments = itemSet.assignInternalItemDamages();

        try (ZipOutputStream zipOutput = new PriorityZipOutputStream(Files.newOutputStream(outputFile.toPath()))) {
            ResourcepackTextureWriter textureWriter = new ResourcepackTextureWriter(itemSet, zipOutput);
            textureWriter.writeBaseTextures();

            ResourcepackModelWriter modelWriter = new ResourcepackModelWriter(itemSet, zipOutput);
            modelWriter.writeCustomItemModels();
            modelWriter.writeCustomBlockModels();

            ResourcepackBlockOverrider blockOverrider = new ResourcepackBlockOverrider(itemSet, zipOutput);
            blockOverrider.overrideMushroomBlocks();

            ResourcepackItemOverrider itemOverrider = new ResourcepackItemOverrider(itemSet, zipOutput, assignments);
            itemOverrider.overrideItems();

            writeAtlases(itemSet, zipOutput);
            writePackMcMeta(itemSet, zipOutput);
            zipOutput.finish();
        }
    }

    private static void writePackMcMeta(ItemSet itemSet, ZipOutputStream zipOutput)
            throws IOException, ProgrammingValidationException {
        int mcVersion = itemSet.getExportSettings().getMcVersion();
        int packFormat = ResourcepackVersionHelper.getPackFormat(mcVersion);

        ZipEntry mcMeta = new ZipEntry("pack.mcmeta");
        zipOutput.putNextEntry(mcMeta);
        PrintWriter jsonWriter = new PrintWriter(zipOutput);
        jsonWriter.println("{");
        jsonWriter.println("    \"pack\": {");
        jsonWriter.println("        \"pack_format\": " + packFormat + ",");
        jsonWriter.println("        \"description\": \"KnokkosCustomItems generated resourcepack\"");
        jsonWriter.println("    }");
        jsonWriter.println("}");
        jsonWriter.flush();
        zipOutput.closeEntry();
    }

    private static void writeAtlases(ItemSet itemSet, ZipOutputStream zipOutput) throws IOException {
        if (itemSet.getExportSettings().getMcVersion() >= VERSION1_19) {
            ZipEntry customAtlas = new ZipEntry("assets/minecraft/atlases/blocks.json");
            zipOutput.putNextEntry(customAtlas);

            PrintWriter jsonWriter = new PrintWriter(zipOutput);
            jsonWriter.println("{");
            jsonWriter.println("    \"sources\": [");
            jsonWriter.println("        {");
            jsonWriter.println("            \"type\": \"directory\",");
            jsonWriter.println("            \"source\": \"customitems\",");
            jsonWriter.println("            \"prefix\": \"customitems/\"");
            jsonWriter.println("        }");
            jsonWriter.println("    ]");
            jsonWriter.println("}");
            jsonWriter.flush();
            zipOutput.closeEntry();
        }
    }
}
