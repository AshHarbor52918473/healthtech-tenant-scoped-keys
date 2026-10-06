package healthtech.tenantkeys;

import java.io.IOException;

/** Coordinates a tenant operator identity with the scoped credential used by appointment operations. */
public final class TenantCredentialService {
    private final InfraiControlPlane infrai;

    public TenantCredentialService(InfraiControlPlane infrai) {
        this.infrai = infrai;
    }

    public TenantCredential provision(TenantRequest request) throws IOException, InterruptedException {
        String userId = infrai.createUser(request.operatorEmail(), request.tenantName() + " appointment operator",
                InfraiControlPlane.idempotencyKey());
        try {
            InfraiControlPlane.IssuedKey issued = infrai.createScopedKey(request.tenantName() + " appointment operations",
                    "[\"appointments:read\",\"appointments:write\",\"notifications:send\"]",
                    InfraiControlPlane.idempotencyKey());
            return new TenantCredential(request.tenantName(), userId, issued.id(), issued.plaintext());
        } catch (IOException | InterruptedException | RuntimeException failure) {
            try {
                infrai.deleteUser(userId);
            } catch (IOException | InterruptedException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public OffboardingDecision offboard(TenantCredential credential) throws IOException, InterruptedException {
        IOException deleteFailure = null;
        try {
            infrai.deleteUser(credential.userId());
        } catch (IOException failure) {
            deleteFailure = failure;
        }
        try {
            infrai.revokeKey(credential.keyId());
        } catch (IOException failure) {
            if (deleteFailure != null) deleteFailure.addSuppressed(failure);
            else throw failure;
        }
        if (deleteFailure != null) throw deleteFailure;
        return OffboardingDecision.complete();
    }

    public record TenantRequest(String tenantName, String operatorEmail) { }

    /** Store plaintextKey now; Infrai returns it only when the key is created. */
    public record TenantCredential(String tenantName, String userId, String keyId, String plaintextKey) { }
}
