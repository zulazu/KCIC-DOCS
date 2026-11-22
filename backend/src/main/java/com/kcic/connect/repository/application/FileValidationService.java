package com.kcic.connect.repository.application;

import com.kcic.connect.masterdata.domain.FilePolicy;
import com.kcic.connect.masterdata.infrastructure.FilePolicyRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Basic upload validation required by AGENTS §8.13: extension, declared MIME,
 * size, and a magic-byte signature when the format has a reliable one. This is
 * not malware scanning; antivirus is outside the pilot scope.
 */
@Service
public class FileValidationService {

    private static final Set<String> ZIP_BASED = Set.of("docx", "xlsx", "pptx");
    private static final Set<String> OLE_BASED = Set.of("doc", "xls", "ppt");
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private final FilePolicyRepository filePolicyRepository;

    public FileValidationService(FilePolicyRepository filePolicyRepository) {
        this.filePolicyRepository = filePolicyRepository;
    }

    public FilePolicy requirePolicy() {
        return filePolicyRepository.findFirstByDefaultPolicyTrueAndActiveTrue()
                .orElseThrow(() -> ApiException.conflict("File policy belum dikonfigurasi."));
    }

    public String validateMetadata(String originalFilename, String contentType, long sizeBytes, FilePolicy policy) {
        String filename = sanitizeFilename(originalFilename);
        String extension = extensionOf(filename);
        if (extension == null || extension.isEmpty()) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "File harus memiliki ekstensi yang diizinkan.");
        }
        boolean extensionAllowed = policy.getAllowedExtensions().stream()
                .anyMatch(allowed -> allowed.trim().toLowerCase(Locale.ROOT).equals(extension));
        if (!extensionAllowed) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Ekstensi ." + extension + " tidak diizinkan oleh file policy.");
        }
        String mediaType = normalizeMediaType(contentType);
        boolean mimeAllowed = policy.getAllowedMimeTypes().stream()
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .anyMatch(type -> type.equals(mediaType));
        if (!mimeAllowed) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Tipe file " + mediaType + " tidak diizinkan oleh file policy.");
        }
        if (sizeBytes <= 0) {
            throw ApiException.validation("file", "File kosong tidak dapat diunggah.");
        }
        if (sizeBytes > policy.getMaxFileSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "Ukuran file melebihi batas " + (policy.getMaxFileSizeBytes() / (1024 * 1024)) + " MB.");
        }
        return filename;
    }

    public void verifySignature(String sanitizedFilename, Path file) {
        String extension = extensionOf(sanitizedFilename);
        byte[] expected;
        if ("pdf".equals(extension)) {
            expected = PDF_SIGNATURE;
        } else if (ZIP_BASED.contains(extension)) {
            expected = ZIP_SIGNATURE;
        } else if (OLE_BASED.contains(extension)) {
            expected = OLE_SIGNATURE;
        } else {
            return;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] header = stream.readNBytes(expected.length);
            if (header.length < expected.length || !Arrays.equals(header, expected)) {
                throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "Isi file tidak sesuai dengan ekstensi ." + extension + ".");
            }
        } catch (IOException ex) {
            throw new StorageException("File unggahan tidak dapat dibaca.", ex);
        }
    }

    public static String sanitizeFilename(String originalFilename) {
        String base = originalFilename == null ? "" : originalFilename;
        base = base.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\p{Cntrl}]", "").trim();
        if (base.isEmpty()) {
            base = "dokumen";
        }
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int semicolon = contentType.indexOf(';');
        String value = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
