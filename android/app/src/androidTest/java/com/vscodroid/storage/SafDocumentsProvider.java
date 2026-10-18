package com.vscodroid.storage;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/** A real provider whose documents exist only in the instrumentation APK's storage. */
public final class SafDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.vscodroid.test.saf.documents";
    public static final String ROOT_ID = "root";
    public static final String FILE_NAME = "notes.txt";

    private final AtomicInteger writeOpens = new AtomicInteger();
    private final AtomicInteger createCalls = new AtomicInteger();

    @Override
    public boolean onCreate() {
        return true;
    }

    private File document() {
        return new File(new File(getContext().getFilesDir(), "saf-documents"), FILE_NAME);
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES
        });
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            .add(Root.COLUMN_TITLE, "VSCodroid test documents")
            .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_CREATE)
            .add(Root.COLUMN_MIME_TYPES, "*/*");
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = documents(projection);
        addDocument(cursor, documentId);
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        if (!ROOT_ID.equals(parentDocumentId)) throw new FileNotFoundException(parentDocumentId);
        MatrixCursor cursor = documents(projection);
        if (document().isFile()) addDocument(cursor, FILE_NAME);
        return cursor;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return ROOT_ID.equals(parentDocumentId) && FILE_NAME.equals(documentId);
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        createCalls.incrementAndGet();
        if (!ROOT_ID.equals(parentDocumentId) || !FILE_NAME.equals(displayName)
                || Document.MIME_TYPE_DIR.equals(mimeType)) {
            throw new FileNotFoundException("Only the fixed test document may be created");
        }
        try {
            if (!document().createNewFile()) throw new IOException("Test document already exists");
        } catch (IOException exception) {
            throw new FileNotFoundException(exception.getMessage());
        }
        return FILE_NAME;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (!FILE_NAME.equals(documentId)) throw new FileNotFoundException(documentId);
        if (mode.contains("w")) writeOpens.incrementAndGet();
        return ParcelFileDescriptor.open(document(), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"reset".equals(method) && !"read".equals(method) && !"stats".equals(method)) {
            return super.call(method, arg, extras);
        }
        try {
            int targetUid = getContext().getPackageManager()
                .getApplicationInfo(SafProviderGrantReceiver.TARGET_PACKAGE, 0).uid;
            if (Binder.getCallingUid() != Process.myUid() && Binder.getCallingUid() != targetUid) {
                throw new SecurityException("Only the fixture owner and target application may control documents");
            }
            File file = document();
            Bundle result = new Bundle();
            switch (method) {
                case "reset":
                    File directory = file.getParentFile();
                    if (!directory.isDirectory() && !directory.mkdirs()) {
                        throw new IOException("Cannot create test document directory");
                    }
                    if (extras != null && extras.getBoolean("empty")) {
                        Files.deleteIfExists(file.toPath());
                    } else {
                        byte[] bytes = extras == null ? null : extras.getByteArray("bytes");
                        if (bytes == null) throw new IllegalArgumentException("Missing fixture bytes");
                        Files.write(file.toPath(), bytes);
                    }
                    writeOpens.set(0);
                    createCalls.set(0);
                    break;
                case "read":
                    result.putByteArray("bytes", Files.readAllBytes(file.toPath()));
                    break;
                case "stats":
                    result.putInt("writeOpens", writeOpens.get());
                    result.putInt("createCalls", createCalls.get());
                    result.putBoolean("exists", file.isFile());
                    break;
                default:
                    throw new IllegalArgumentException(method);
            }
            return result;
        } catch (IOException | android.content.pm.PackageManager.NameNotFoundException exception) {
            throw new IllegalStateException("Cannot access disposable provider fixture", exception);
        }
    }

    private MatrixCursor documents(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED
        });
    }

    private void addDocument(MatrixCursor cursor, String id) throws FileNotFoundException {
        boolean root = ROOT_ID.equals(id);
        File file = document();
        if (!root && (!FILE_NAME.equals(id) || !file.isFile())) throw new FileNotFoundException(id);
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, root ? "VSCodroid test documents" : FILE_NAME)
            .add(Document.COLUMN_MIME_TYPE, root ? Document.MIME_TYPE_DIR : "text/plain")
            .add(Document.COLUMN_FLAGS, root ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE)
            .add(Document.COLUMN_SIZE, root ? 0L : file.length())
            .add(Document.COLUMN_LAST_MODIFIED, root ? 0L : file.lastModified());
    }
}
