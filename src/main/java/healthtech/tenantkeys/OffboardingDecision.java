package healthtech.tenantkeys;

/** The caller records completion only after both tenant-owned resources have been removed. */
public record OffboardingDecision(boolean userDeleted, boolean keyRevoked) {
    public static OffboardingDecision complete() {
        return new OffboardingDecision(true, true);
    }

    public boolean isComplete() {
        return userDeleted && keyRevoked;
    }
}
