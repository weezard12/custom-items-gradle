package nl.knokko.customitems.item.model;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import nl.knokko.customitems.bithelper.BitInput;
import nl.knokko.customitems.bithelper.BitOutput;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.trouble.UnknownEncodingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipOutputStream;

import static nl.knokko.customitems.MCVersions.VERSION26_3;

public class LegacyCustomItemModel implements ItemModel {

    public static LegacyCustomItemModel loadLegacyCustom(BitInput input, ItemSet.Side side) throws UnknownEncodingException {
        byte encoding = input.readByte();
        if (encoding < 1 || encoding > 2) throw new UnknownEncodingException("LegacyCustomItemModel", encoding);

        if (encoding == 1 || side == ItemSet.Side.EDITOR) {
            return new LegacyCustomItemModel(input.readByteArray());
        } else return new LegacyCustomItemModel(null);
    }

    private final byte[] rawModel;

    public LegacyCustomItemModel(byte[] rawModel) {
        this.rawModel = rawModel;
    }

    public byte[] getRawModel() {
        return rawModel;
    }

    @Override
    public void write(
            ZipOutputStream zipOutput, String itemName, String textureName,
            DefaultModelType defaultModelType, boolean isLeatherArmor
    ) throws IOException {
        zipOutput.write(rawModel);
        zipOutput.flush();
    }

    @Override
    public void write(
            ZipOutputStream zipOutput, String itemName, String textureName,
            DefaultModelType defaultModelType, boolean isLeatherArmor, int mcVersion
    ) throws IOException {
        if (mcVersion < VERSION26_3) {
            write(zipOutput, itemName, textureName, defaultModelType, isLeatherArmor);
            return;
        }
        try {
            JsonObject model = (JsonObject) Jsoner.deserialize(new String(rawModel, StandardCharsets.UTF_8));
            ModelFormatHelper.adapt(model, mcVersion);
            zipOutput.write(Jsoner.serialize(model).getBytes(StandardCharsets.UTF_8));
            zipOutput.flush();
        } catch (JsonException failure) {
            throw new IOException("Invalid custom item model", failure);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LegacyCustomItemModel && Arrays.equals(this.rawModel, ((LegacyCustomItemModel) other).rawModel);
    }

    @Override
    public void save(BitOutput output, ItemSet.Side targetSide) {
        output.addByte(MODEL_TYPE_CUSTOM_LEGACY);
        output.addByte((byte) 2);

        if (targetSide == ItemSet.Side.EDITOR) output.addByteArray(rawModel);
    }
}
