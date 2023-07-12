package indi.sophronia.tools;

import indi.sophronia.tools.output.TranslationOutput;
import indi.sophronia.tools.util.Property;
import indi.sophronia.tools.util.RPC;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public enum Main implements ClipboardOwner {
    INSTANCE;

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
                    if (!clipboard.isDataFlavorAvailable(dataFlavor)) {
                        return;
                    }

                    Object fromText = clipboard.getData(dataFlavor);
                    if (fromText instanceof InputStream is) {
                        int c;
                        while ((c = is.read()) != -1) {
                            translationOutput.write(c);
                        }
                        translationOutput.flush();
                    }

                    clipboard.setContents(EmptyContent.INSTANCE, INSTANCE);
                } catch (UnsupportedFlavorException | IOException ex) {
                    ex.printStackTrace();
                }
            });

            while (true) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
            }
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        try {
            clipboard.setContents(clipboard.getContents(null), INSTANCE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private enum EmptyContent implements Transferable {
        INSTANCE;

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return false;
        }

        @NotNull
        @Override
        public Object getTransferData(DataFlavor flavor) {
            return "";
        }
    }
}
