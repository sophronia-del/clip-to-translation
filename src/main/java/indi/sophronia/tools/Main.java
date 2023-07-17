package indi.sophronia.tools;

import indi.sophronia.tools.output.TranslationOutput;
import indi.sophronia.tools.util.Property;
import indi.sophronia.tools.util.RPC;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public enum Main implements ClipboardOwner {
    INSTANCE;

    public static void main(String[] args) throws IOException {
        Properties properties = Property.properties();

        RPC.init(properties);

        System.setErr(new PrintStream(properties.getProperty("error.file", "error.log")));


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
                } catch (Exception ignored) {
                }
            }
        }

        try (TranslationOutput translationOutput = new TranslationOutput(properties, charset)) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.addFlavorListener(e -> {
                try {
                    Transferable contents = clipboard.getContents(null);
                    if (contents.isDataFlavorSupported(dataFlavor)) {
                        Object fromText = contents.getTransferData(dataFlavor);
                        if (fromText instanceof InputStream is) {
                            int c;
                            while ((c = is.read()) != -1) {
                                translationOutput.write(c);
                            }
                            translationOutput.flush();
                        } else {
                            System.err.printf("text type: %s\n", fromText.getClass().getName());
                        }

                        clipboard.setContents(EmptyContent.INSTANCE, INSTANCE);
                    } else {
                        clipboard.setContents(contents, INSTANCE);
                    }
                } catch (UnsupportedFlavorException | IOException ex) {
                    System.err.println(ex.getMessage());
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
            System.err.println(e.getMessage());
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
