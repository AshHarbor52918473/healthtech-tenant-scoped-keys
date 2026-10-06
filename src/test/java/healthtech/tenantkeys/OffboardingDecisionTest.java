package healthtech.tenantkeys;

/** Focused business test: an offboarding record is complete only with both ownership actions. */
public final class OffboardingDecisionTest {
    public static void main(String[] args) {
        assertFalse(new OffboardingDecision(true, false).isComplete(), "a retained key keeps offboarding open");
        assertFalse(new OffboardingDecision(false, true).isComplete(), "a retained user keeps offboarding open");
        assertTrue(OffboardingDecision.complete().isComplete(), "both actions close offboarding");
        System.out.println("Offboarding business decision passed");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }
}
