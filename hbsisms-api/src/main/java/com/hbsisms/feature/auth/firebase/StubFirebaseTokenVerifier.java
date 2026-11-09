package com.hbsisms.feature.auth.firebase;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev/test verifier — no Firebase network calls.
 * <p>
 * Token format: {@code stub.&lt;uid&gt;.&lt;phone&gt;} e.g. {@code stub.uid-kho1.0901234567}
 * or {@code stub.uid-kho1.+84901234567}.
 */
@Component
@ConditionalOnProperty(prefix = "app.security.firebase", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubFirebaseTokenVerifier implements FirebaseTokenVerifier {

    public static final String PREFIX = "stub.";

    @Override
    public VerifiedFirebaseToken verify(String idToken) {
        String raw = Strings.trimToNull(idToken);
        if (raw == null || !raw.startsWith(PREFIX)) {
            throw ApiException.badRequest("idToken không hợp lệ");
        }
        String rest = raw.substring(PREFIX.length());
        int sep = rest.indexOf('.');
        if (sep <= 0 || sep >= rest.length() - 1) {
            throw ApiException.badRequest("idToken không hợp lệ");
        }
        String uid = Strings.trimToNull(rest.substring(0, sep));
        String phone = Strings.trimToNull(rest.substring(sep + 1));
        if (uid == null || phone == null) {
            throw ApiException.badRequest("idToken không hợp lệ");
        }
        return new VerifiedFirebaseToken(uid, phone);
    }
}
