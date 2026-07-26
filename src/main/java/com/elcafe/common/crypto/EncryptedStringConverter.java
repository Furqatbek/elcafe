package com.elcafe.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that encrypts a String attribute on the way to the database and decrypts it on the way
 * back, via {@link CredentialCrypto}. Applied (with {@code @Convert}) to credential columns —
 * Instagram/Telegram tokens and secrets — so a database dump no longer yields usable credentials.
 *
 * <p>Inert until {@code ELCAFE_ENCRYPTION_KEY} is provisioned, and transparent to legacy plaintext rows;
 * see {@link CredentialCrypto}. {@code autoApply = false}: it is opted into per field, never applied to
 * every String column by accident.
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return CredentialCrypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return CredentialCrypto.decrypt(dbData);
    }
}
