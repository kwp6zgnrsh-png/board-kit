package io.github.kwp6zgnrsh.boardkitapi.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import io.github.kwp6zgnrsh.boardkitapi.file.exception.InvalidFileRequestException;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileFormat;

@Component
public class FileSignatureDetector {

	private static final int HEADER_SIZE = 8;

	public FileFormat detect(MultipartFile file, String extension) {
		byte[] header = readHeader(file);

		if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
			return FileFormat.JPEG;
		}
		if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			return FileFormat.PNG;
		}
		if (startsWith(header, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61) || startsWith(header, 0x47, 0x49, 0x46, 0x38, 0x39,
			0x61)) {
			return FileFormat.GIF;
		}
		if (startsWith(header, 0x50, 0x4B, 0x03, 0x04) || startsWith(header, 0x50, 0x4B, 0x05, 0x06) || startsWith(
			header, 0x50, 0x4B, 0x07, 0x08)) {
			return FileFormat.ZIP;
		}
		if (startsWith(header, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
			return FileFormat.PDF;
		}
		if ("txt".equalsIgnoreCase(extension)) {
			validateUtf8Text(file);
			return FileFormat.TXT;
		}

		throw new InvalidFileRequestException("지원하지 않는 파일 형식입니다.");
	}

	private void validateUtf8Text(MultipartFile file) {
		var decoder = StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);

		try (Reader reader = new InputStreamReader(file.getInputStream(), decoder)) {
			char[] buffer = new char[4096];
			int count;

			while ((count = reader.read(buffer)) != -1) {
				for (int index = 0; index < count; index++) {
					char character = buffer[index];

					if (Character.isISOControl(character) && character != '\t' && character != '\n'
						&& character != '\r') {
						throw new InvalidFileRequestException("TXT 파일에 사용할 수 없는 제어 문자가 포함되어 있습니다.");
					}
				}
			}
		} catch (CharacterCodingException exception) {
			throw new InvalidFileRequestException("TXT 파일은 UTF-8 인코딩으로 저장해 주세요.", exception);
		} catch (IOException exception) {
			throw new InvalidFileRequestException("TXT 파일을 읽을 수 없습니다.", exception);
		}
	}

	private byte[] readHeader(MultipartFile file) {
		try (InputStream input = file.getInputStream()) {
			return input.readNBytes(HEADER_SIZE);
		} catch (IOException exception) {
			throw new InvalidFileRequestException("파일 형식을 확인할 수 없습니다.", exception);
		}
	}

	private boolean startsWith(byte[] content, int... signature) {
		if (content.length < signature.length) {
			return false;
		}

		for (int index = 0; index < signature.length; index++) {
			if ((content[index] & 0xFF) != signature[index]) {
				return false;
			}
		}

		return true;
	}
}