package uk.gov.di.authentication.shared.jwt;

import com.amazonaws.util.Base64;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import uk.gov.di.authentication.shared.services.KmsConnectionService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KmsJwsSignerTest {

    private final KmsConnectionService kms = mock(KmsConnectionService.class);

    @Test
    void shouldReturnEmptyBaseUrlWithNoData() throws JOSEException {
        var signer = new KmsJwsSigner(kms, "rsa-signing-key", "ec-signing-key");
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
        var emptyData = new byte[0];

        assertThat(signer.sign(header, emptyData), is(new Base64URL("")));
    }

    @Test
    void shouldSignDataWithRS256IfNonEmpty() throws JOSEException {
        var signer = new KmsJwsSigner(kms, "rsa-signing-key", "ec-signing-key");
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
        var data = "input".getBytes();

        when(kms.sign("rsa-signing-key", SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256, data))
                .thenReturn("output".getBytes());

        assertThat(signer.sign(header, data), is(Base64URL.encode("output")));
    }

    @Test
    void shouldSignDataWithES256IfNonEmpty() throws JOSEException {
        var signer = new KmsJwsSigner(kms, "rsa-signing-key", "ec-signing-key");
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
        var data = "input".getBytes();

        // "output" signed with disposable key
        var output =
                Base64.decode(
                        "MEUCICPb0OfprJaOLMB8mTsZniBwShawFyxujrtriH4gykAXAiEAjHJfq/lM2XgbvzjzBn1Lw9nMdLjzbCserDIw7MfzfPs=");

        when(kms.sign("ec-signing-key", SigningAlgorithmSpec.ECDSA_SHA_256, data))
                .thenReturn(output);

        var expectedOutput =
                Base64URL.encode(
                        ECDSA.transcodeSignatureToConcat(
                                output, ECDSA.getSignatureByteArrayLength(JWSAlgorithm.ES256)));

        assertThat(signer.sign(header, data), is(expectedOutput));
    }
}
