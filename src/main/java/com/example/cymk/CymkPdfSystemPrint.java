package com.example.cymk;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Creates a one-page A4 PDF in the running OS temporary directory, with the
 * bundled logo in one random square cell, then opens the OS PDF viewer's print UI.
 */
public final class CymkPdfSystemPrint {
    private static final int COLUMNS = 13;
    private static final float MM_TO_POINTS = 72f / 25.4f;
    private static final float MARGIN_MM = 2f;

    public static void main(String[] args) throws Exception {
        Path pdf = createTemporaryPdf();
        System.out.println("Created temporary print PDF: " + pdf.toAbsolutePath());
        openSystemPrintDialog(pdf);
    }

    private static Path createTemporaryPdf() throws IOException {
        Path temp = Files.createTempFile("cymk-printer-test-", ".pdf");
        temp.toFile().setReadable(true, false);

        try (InputStream imageStream = CymkPdfSystemPrint.class.getResourceAsStream("/cymk.jpg")) {
            if (imageStream == null) throw new IOException("Bundled /cymk.jpg resource not found.");
            BufferedImage bufferedImage = ImageIO.read(imageStream);
            if (bufferedImage == null) throw new IOException("Could not decode bundled cymk.jpg.");

            PDRectangle a4 = PDRectangle.A4;
            float pageW = a4.getWidth();
            float pageH = a4.getHeight();
            float margin = MARGIN_MM * MM_TO_POINTS;
            float printableW = pageW - 2 * margin;
            float printableH = pageH - 2 * margin;
            float cell = printableW / COLUMNS;
            int rows = (int) Math.floor(printableH / cell);
            Random random = new Random();
            int selectedCol = random.nextInt(COLUMNS);
            int selectedRow = random.nextInt(rows);

            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(document, bufferedImage);

                float gridHeight = rows * cell;
                float gridTop = (pageH + gridHeight) / 2f;
                float x = margin + selectedCol * cell;
                // PDF coordinates originate at the bottom-left.
                float yTop = gridTop - selectedRow * cell;
                float inset = 0.5f * MM_TO_POINTS;
                float available = cell - 2 * inset;
                float scale = Math.min(available / image.getWidth(), available / image.getHeight());
                float drawW = image.getWidth() * scale;
                float drawH = image.getHeight() * scale;
                float drawX = x + (cell - drawW) / 2f;
                float drawY = (yTop - cell) + (cell - drawH) / 2f;

                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(image, drawX, drawY, drawW, drawH);
                }
                document.save(temp.toFile());
            }
        }
        return temp;
    }

    private static void openSystemPrintDialog(Path pdf) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String absolutePath = pdf.toAbsolutePath().toString();

        if (os.contains("mac")) {
            // 1. Open the PDF in Preview via LaunchServices (/usr/bin/open).
            // LaunchServices grants Preview the required macOS App Sandbox extension
            // to access files located in temporary directories (/var/folders/...).
            runAndCheck(new ProcessBuilder("/usr/bin/open", "-a", "Preview", absolutePath));

            // 2. Open Preview's native macOS Print dialog (Cmd+P) via AppleScript.
            // macOS may ask for Accessibility permission for System Events.
            String script = """
                    tell application "Preview"
                      activate
                    end tell
                    delay 1.5
                    tell application "System Events"
                      tell process "Preview"
                        set frontmost to true
                        keystroke "p" using {command down}
                      end tell
                    end tell
                    """;
            runAndCheck(new ProcessBuilder("/usr/bin/osascript", "-e", script));
        } else if (os.contains("win")) {
            // Launch the PDF using the OS default viewer. The user can invoke its
            // native Print UI; Windows has no universal command to force every PDF
            // viewer to open a dialog.
            new ProcessBuilder("cmd", "/c", "start", "", absolutePath).start();
            System.out.println("PDF opened in the default viewer. Use its Print command.");
        } else {
            // xdg-open is the standard desktop opener on most Linux desktop systems.
            new ProcessBuilder("xdg-open", absolutePath).start();
            System.out.println("PDF opened in the default viewer. Use its Print command.");
        }
    }

    private static void runAndCheck(ProcessBuilder builder) throws IOException, InterruptedException {
        Process process = builder.redirectErrorStream(true).start();
        boolean exited = process.waitFor(15, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            throw new IOException("Timed out while executing: " + builder.command());
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IOException("Command failed: " + builder.command() + "\nOutput: " + output +
                    "\nIf this failed during osascript, check macOS Privacy & Security > Accessibility permissions for the terminal/Java process.");
        }
    }
}
