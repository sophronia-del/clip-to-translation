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
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class Main {
    public static void main(String[] args) throws IOException {
        Properties properties = Property.properties();

        RPC.init(properties);

        try (TranslationOutput translationOutput = new TranslationOutput(properties)) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.addFlavorListener(e -> {
                try {
                    Object data = clipboard.getData(new DataFlavor("text/plain"));
                    if (data instanceof InputStream is) {
                        int c;
                        while ((c = is.read()) != -1) {
                            translationOutput.write(c);
                        }
                        translationOutput.flush();
                    }
                } catch (UnsupportedFlavorException | IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
            });

            while (true) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
            }
        }
    }
}
