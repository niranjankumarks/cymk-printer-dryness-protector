# CYMK PDF System Print

This Maven Java 17 project creates a one-page A4 PDF in the running operating system's temporary directory, then opens it for printing.

## Build a runnable JAR
```bash
mvn clean package
```
Output:
`target/cymk-pdf-system-print-1.2.0.jar`

## Run
```bash
java -jar target/cymk-pdf-system-print-1.2.0.jar
```

## macOS behavior
The app writes a PDF under the OS temp directory (typically `/var/folders/.../T/`) and opens it in Preview, then asks Preview to open the native macOS Print dialog using Command-P. macOS may require Accessibility permission for the terminal or Java process under System Settings → Privacy & Security → Accessibility.

## Other operating systems
On Windows and Linux, the app opens the PDF in the default PDF viewer. There is no universal OS-level command that forces every third-party PDF viewer to show its print dialog; invoke Print in the viewer.

## Page design
- A4 portrait.
- 13 columns; square cell size derived from A4 width and 2 mm nominal margins.
- One random cell contains the bundled CYMK logo; all other areas are blank.
- Image scaled proportionally with a small inset.
- PDF is created in the OS temp directory with Files.createTempFile (cleaned up by the OS).
- No cell borders are drawn to minimize ink.
