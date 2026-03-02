import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.InOrder;

class LibraryServiceTest {
    // EP test
    @ParameterizedTest(name = "{0} - {1}")
    @CsvFileSource(resources = "/EPs.csv", numLinesToSkip = 1)
    void test_from_Eps_csv(
            String testId,
            String testName,
            String resourceIdText,
            String memberEmail,
            String repoSetup,
            String emailSetup,
            String expectedResult
    ) throws Exception {

        ResourceRepository repo = mock(ResourceRepository.class);
        EmailProvider email = mock(EmailProvider.class);
        LibraryService service = new LibraryService(email, repo);

        UUID id = parseResourceId(resourceIdText);

        applyRepoSetup(repo, id, repoSetup);
        applyEmailSetup(email, emailSetup);

        assertExpected(expectedResult, () -> service.checkoutResource(id, memberEmail));

        if (id == null) {
            verifyNoInteractions(repo, email);
        } else if (repoSetup != null && repoSetup.toLowerCase().contains("isresourceavailable=false")) {
            verify(repo).isResourceAvailable(id);
            verify(repo, never()).updateStatus(any(), anyBoolean());
            verifyNoInteractions(email);
        }
    }
    // BVA test
    @ParameterizedTest(name = "{0} - {1} - {2}")
    @CsvFileSource(resources = "/BVAs.csv", numLinesToSkip = 1)
    void test_from_BVAs_csv(
            String testId,
            String inputVariable,
            String boundaryCondition,
            String testValue,
            String expectedResult
    ) throws Exception {

        ResourceRepository repo = mock(ResourceRepository.class);
        EmailProvider email = mock(EmailProvider.class);
        LibraryService service = new LibraryService(email, repo);

        if ("resourceId".equalsIgnoreCase(inputVariable)) {
            UUID id = parseResourceId(testValue);
            String memberEmail = "a@b.com";

            if (id != null) {
                when(repo.isResourceAvailable(id)).thenReturn(false);
            }

            boolean result = service.checkoutResource(id, memberEmail);

            if (id == null) {
                assertFalse(result);
                verifyNoInteractions(repo, email);
            } else {
                assertFalse(result);
                verify(repo).isResourceAvailable(id);
                verify(repo, never()).updateStatus(any(), anyBoolean());
                verifyNoInteractions(email);
            }
        } else if ("memberEmail".equalsIgnoreCase(inputVariable)) {
            UUID id = UUID.randomUUID();
            String memberEmail = unquote(testValue);

            when(repo.isResourceAvailable(id)).thenReturn(true);
            when(repo.updateStatus(id, false)).thenReturn(true);

            if (memberEmail.isEmpty()) {
                when(email.sendEmail(eq(memberEmail), anyString())).thenReturn(false);
                assertThrows(EmailFailureException.class, () -> service.checkoutResource(id, memberEmail));
            } else {
                when(email.sendEmail(eq(memberEmail), anyString())).thenReturn(true);
                assertTrue(service.checkoutResource(id, memberEmail));
            }
        } else {
            fail("Unknown Input Variable in BVAs.csv: " + inputVariable);
        }
    }
    // DT test
    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/DTs.csv", numLinesToSkip = 1)
    void test_from_DTs_csv(
            String ruleId,
            String idNull,
            String available,
            String updateOk,
            String emailOk,
            String expectedOutcome
    ) throws Exception {

        ResourceRepository repo = mock(ResourceRepository.class);
        EmailProvider email = mock(EmailProvider.class);
        LibraryService service = new LibraryService(email, repo);

        UUID id = "Yes".equalsIgnoreCase(idNull) ? null : UUID.randomUUID();
        String memberEmail = "a@b.com";

        if (id != null) {
            when(repo.isResourceAvailable(id)).thenReturn("Yes".equalsIgnoreCase(available));

            if ("Yes".equalsIgnoreCase(available)) {
                if ("No".equalsIgnoreCase(updateOk)) {
                    when(repo.updateStatus(id, false)).thenReturn(false);
                } else if ("Yes".equalsIgnoreCase(updateOk)) {
                    when(repo.updateStatus(id, false)).thenReturn(true);
                }

                if ("Yes".equalsIgnoreCase(updateOk)) {
                    if ("No".equalsIgnoreCase(emailOk)) {
                        when(email.sendEmail(eq(memberEmail), anyString())).thenReturn(false);
                    } else if ("Yes".equalsIgnoreCase(emailOk)) {
                        when(email.sendEmail(eq(memberEmail), anyString())).thenReturn(true);
                    }
                }
            }
        }

        assertExpected(expectedOutcome, () -> service.checkoutResource(id, memberEmail));

        // Verify order for success case only
        if ("Return true".equalsIgnoreCase(expectedOutcome) && id != null) {
            InOrder order = inOrder(repo, email);
            order.verify(repo).isResourceAvailable(id);
            order.verify(repo).updateStatus(id, false);
            order.verify(email).sendEmail(eq(memberEmail), contains(id.toString()));
        }
    }

    private static UUID parseResourceId(String text) {
        if (text == null) return UUID.randomUUID();
        String t = text.trim();

        if (t.equalsIgnoreCase("null")) return null;
        if (t.equalsIgnoreCase("valid uuid") || t.equalsIgnoreCase("random uuid")) return UUID.randomUUID();

        try {
            return UUID.fromString(t);
        } catch (IllegalArgumentException ex) {
            return UUID.randomUUID();
        }
    }

    private static String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static void applyRepoSetup(ResourceRepository repo, UUID id, String repoSetup) throws Exception {
        if (id == null || repoSetup == null) return;

        String s = repoSetup.toLowerCase();

        if (s.contains("isresourceavailable=false") || s.contains("isavailable=false")) {
            when(repo.isResourceAvailable(id)).thenReturn(false);
        } else if (s.contains("isresourceavailable=true") || s.contains("isavailable=true")) {
            when(repo.isResourceAvailable(id)).thenReturn(true);
        }

        if (s.contains("updatestatus=true") || s.contains("checkout=true")) {
            when(repo.updateStatus(id, false)).thenReturn(true);
        } else if (s.contains("updatestatus=false") || s.contains("checkout=false")) {
            when(repo.updateStatus(id, false)).thenReturn(false);
        } else if (s.contains("updatestatus throws") || s.contains("checkout throws")) {
            when(repo.updateStatus(id, false)).thenThrow(new DatabaseFailureException("simulated db failure"));
        }
    }

    private static void applyEmailSetup(EmailProvider email, String emailSetup) throws Exception {
        if (emailSetup == null) return;
        String s = emailSetup.toLowerCase();

        if (s.contains("sendemail=true")) {
            when(email.sendEmail(anyString(), anyString())).thenReturn(true);
        } else if (s.contains("sendemail=false")) {
            when(email.sendEmail(anyString(), anyString())).thenReturn(false);
        } else if (s.contains("sendemail throws")) {
            when(email.sendEmail(anyString(), anyString()))
                    .thenThrow(new EmailFailureException("simulated email failure"));
        }
    }

    @FunctionalInterface
    interface ThrowingCall {
        boolean run() throws Exception;
    }

    private static void assertExpected(String expectedText, ThrowingCall call) {
        String e = expectedText == null ? "" : expectedText.trim().toLowerCase();

        if (e.equals("return false")) {
            assertDoesNotThrow(() -> assertFalse(call.run()));
        } else if (e.equals("return true")) {
            assertDoesNotThrow(() -> assertTrue(call.run()));
        } else if (e.contains("throw databasefailureexception")) {
            assertThrows(DatabaseFailureException.class, () -> call.run());
        } else if (e.contains("throw emailfailureexception")) {
            assertThrows(EmailFailureException.class, () -> call.run());
        } else if (e.contains("throw resourceupdateexception")) {
            assertThrows(DatabaseFailureException.class, () -> call.run());
        } else if (e.contains("throw emailsendexception")) {
            assertThrows(EmailFailureException.class, () -> call.run());
        } else {
            fail("Unknown expected result text: " + expectedText);
        }
    }
}