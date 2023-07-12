package indi.sophronia.tools;

import indi.sophronia.tools.output.TranslationOutput;
import indi.sophronia.tools.util.Property;
import indi.sophronia.tools.util.RPC;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class Main {
    public static void main(String[] args) throws IOException {
        Properties properties = Property.properties();

        RPC.init(properties);


        DataFlavor dataFlavor = DataFlavor.getTextPlainUnicodeFlavor();

        Charset charset = Charset.defaultCharset();
        for (String kv : dataFlavor.getMimeType().split(";")) {
            String[] pair = kv.split("=");
            if (pair.length != 2) {
                continue;
            }
            if ("charset".equals(pair[0].trim())) {
                try {
                    charset = Charset.forName(pair[1].trim());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        try (TranslationOutput translationOutput = new TranslationOutput(properties, charset)) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.addFlavorListener(e -> {
                try {
                    DataFlavor.getTextPlainUnicodeFlavor().getMimeType();
                    Object data = clipboard.getData(dataFlavor);
                    if (data instanceof InputStream is) {
                        int c;
                        while ((c = is.read()) != -1) {
                            translationOutput.write(c);
                        }
                        translationOutput.flush();
                    }
                } catch (UnsupportedFlavorException | IOException ex) {
                    ex.printStackTrace();
                }
            });

            while (true) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
            }
        }
    }
}
