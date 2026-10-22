package com.jobtrack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.domain.Interview;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.StatusChange;
import com.jobtrack.repository.DocumentRepository;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusChangeRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.DashboardService.DashboardView;
import com.jobtrack.service.FileStorageService;

/** The end-to-end scenarios that must pass before the first version is declared done. */
class EndToEndScenariosTest extends IntegrationTestBase {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired JobApplicationRepository apps;
    @Autowired StatusChangeRepository history;
    @Autowired InterviewRepository interviews;
    @Autowired DocumentRepository documents;
    @Autowired FileStorageService storage;

    // ------------------------------------------------------------------ Helpers

    static MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", ("%PDF-1.4\n% " + filename).getBytes(UTF_8));
    }

    private void upload(AppUserDetails me, UUID appId, DocumentType type, MockMultipartFile file) throws Exception {
        mvc.perform(multipart("/applications/{id}/documents", appId).file(file)
                        .param("type", type.name()).with(user(me)).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private List<StatusChange> timeline(UUID appId) {
        return history.findTimeline(appId);
    }

    private long storedFileCount() throws IOException {
        try (Stream<java.nio.file.Path> s = Files.list(STORAGE_DIR)) {
            return s.count();
        }
    }

    @SuppressWarnings("unchecked")
    private List<JobApplication> listResult(MvcResult r) {
        Page<JobApplication> page = (Page<JobApplication>) r.getModelAndView().getModel().get("page");
        return page.getContent();
    }

    // ------------------------------------------------------------------ 1. Register, sign in, sign out

    @Test
    void userCanRegisterWithEmailAndPassword_thenLoginAndLogout() throws Exception {
        String email = "reg_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        // Only an email and a password are needed
        mvc.perform(post("/register").with(csrf())
                        .param("email", "  " + email.toUpperCase() + " ")
                        .param("password", "password123").param("confirmPassword", "password123"))
                .andExpect(redirectedUrl("/login"));

        // The email is the sign-in name (stored trimmed and lower-cased)
        mvc.perform(formLogin("/login").userParameter("email").user(email).password("password123"))
                .andExpect(authenticated().withUsername(email).withRoles("USER"))
                .andExpect(redirectedUrl("/dashboard"));
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getLastLoginAt()).isNotNull();

        mvc.perform(formLogin("/login").userParameter("email").user(email).password("wrong-password"))
                .andExpect(unauthenticated());

        mvc.perform(logout()).andExpect(redirectedUrl("/login")); // "signed out" message travels as a flash attribute

        // Unauthenticated access to a protected page redirects to the login page
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void registrationRejectsDuplicatesAndMismatchedPasswords() throws Exception {
        AppUserDetails existing = newUser("dup");
        mvc.perform(post("/register").with(csrf())
                        .param("email", existing.getUsername().toUpperCase())
                        .param("password", "password123").param("confirmPassword", "password123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "email"));

        mvc.perform(post("/register").with(csrf())
                        .param("email", "newbie_x1@example.com")
                        .param("password", "password123").param("confirmPassword", "different123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));

        mvc.perform(post("/register").with(csrf())
                        .param("email", "not-an-email").param("password", "short").param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "email", "password"));
    }

    // ------------------------------------------------------------------ 2. Create an application + JD text + upload CV / cover letter

    @Test
    void createApplicationWithJdTextAndUploadMaterials() throws Exception {
        AppUserDetails me = newUser("alice");
        UUID id = createApplication(me, "Acme", "Graduate Software Engineer", "Java, Spring Boot, SQL");

        JobApplication app = apps.findById(id).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVED);   // rule 9: defaults to SAVED
        assertThat(app.getAppliedDate()).isNull();
        assertThat(app.getJdText()).isEqualTo("Java, Spring Boot, SQL");
        assertThat(app.getOwner().getId()).isEqualTo(me.getId());
        assertThat(timeline(id)).hasSize(1);                               // initial status recorded in history

        upload(me, id, DocumentType.CV, pdf("cv.pdf"));
        upload(me, id, DocumentType.COVER_LETTER, pdf("cover-letter.pdf"));
        upload(me, id, DocumentType.JD, pdf("jd.pdf"));                   // rule 5: JD text and JD file coexist
        upload(me, id, DocumentType.CV, pdf("cv-v2.pdf"));                 // rule 6: new version, no overwrite

        List<ApplicationDocument> cvs = documents.findByApplicationIdAndDocTypeOrderByVersionNoDesc(id, DocumentType.CV);
        assertThat(cvs).extracting(ApplicationDocument::getVersionNo).containsExactly(2, 1);
        assertThat(cvs).allSatisfy(d -> assertThat(storage.exists(d.getStoredFilename())).isTrue());
        assertThat(cvs.get(0).getStoredFilename()).isNotEqualTo(cvs.get(1).getStoredFilename());
        assertThat(documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(id)).hasSize(4);
        assertThat(apps.findById(id).orElseThrow().getJdText()).isEqualTo("Java, Spring Boot, SQL");

        mvc.perform(get("/documents/{id}/download", cvs.get(1).getId()).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("cv.pdf")));

        // Viewing opens the PDF inline (the page links it with target="_blank"), with a locked-down CSP
        mvc.perform(get("/documents/{id}/view", cvs.get(1).getId()).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));

        mvc.perform(get("/applications/{id}", id).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(view().name("applications/detail"));
    }

    @Test
    void newApplicationCanBeCreatedWithCvCoverLetterAndJdAtOnce() throws Exception {
        AppUserDetails me = newUser("attach");
        MvcResult r = mvc.perform(multipart("/applications")
                        .file(new MockMultipartFile("cvFile", "my-cv.pdf", "application/pdf", "%PDF-1.4 cv".getBytes(UTF_8)))
                        .file(new MockMultipartFile("coverLetterFile", "cl.pdf", "application/pdf", "%PDF-1.4 cl".getBytes(UTF_8)))
                        .file(new MockMultipartFile("jdFile", "jd.pdf", "application/pdf", "%PDF-1.4 jd".getBytes(UTF_8)))
                        .param("company", "Attach Co").param("positionTitle", "Engineer")
                        .with(user(me)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", containsString("3 attachments")))
                .andReturn();
        UUID id = UUID.fromString(r.getResponse().getRedirectedUrl().substring("/applications/".length()));

        assertThat(documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(id))
                .extracting(ApplicationDocument::getDocType)
                .containsExactlyInAnyOrder(DocumentType.CV, DocumentType.COVER_LETTER, DocumentType.JD);
        assertThat(documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(id))
                .allSatisfy(d -> assertThat(storage.exists(d.getStoredFilename())).isTrue());

        // Attachments are optional: only a CV, the other inputs left empty
        MvcResult r2 = mvc.perform(multipart("/applications")
                        .file(new MockMultipartFile("cvFile", "cv.pdf", "application/pdf", "%PDF-1.4 cv".getBytes(UTF_8)))
                        .file(new MockMultipartFile("coverLetterFile", "", "application/octet-stream", new byte[0]))
                        .param("company", "Only CV Co").param("positionTitle", "Engineer")
                        .with(user(me)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        UUID id2 = UUID.fromString(r2.getResponse().getRedirectedUrl().substring("/applications/".length()));
        assertThat(documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(id2)).hasSize(1);
    }

    @Test
    void anInvalidAttachmentCreatesNothing() throws Exception {
        AppUserDetails me = newUser("badattach");
        long appsBefore = apps.count();
        long filesBefore = storedFileCount();

        // The CV is fine, the cover letter is a Word file: the whole create is rolled back, including the CV on disk
        mvc.perform(multipart("/applications")
                        .file(new MockMultipartFile("cvFile", "cv.pdf", "application/pdf", "%PDF-1.4 cv".getBytes(UTF_8)))
                        .file(new MockMultipartFile("coverLetterFile", "cl.docx", "application/octet-stream",
                                new byte[] {'P', 'K', 3, 4, 0, 0}))
                        .param("company", "Rollback Co").param("positionTitle", "Engineer")
                        .with(user(me)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("applications/form"))
                .andExpect(model().attribute("attachmentErrors",
                        org.hamcrest.Matchers.hasKey("COVER_LETTER")));

        assertThat(apps.count()).isEqualTo(appsBefore);
        assertThat(storedFileCount()).isEqualTo(filesBefore);
    }

    // ------------------------------------------------------------------ 3. Edit and delete (files are cleaned up on delete)

    @Test
    void userCanEditAndDeleteApplication_andFilesAreRemoved() throws Exception {
        AppUserDetails me = newUser("editor");
        AppUserDetails other = newUser("other");
        UUID id = createApplication(me, "Acme", "Engineer", null);

        mvc.perform(post("/applications/{id}", id).with(user(me)).with(csrf())
                        .param("company", "Acme NZ").param("positionTitle", "Graduate Engineer")
                        .param("location", "Wellington")
                        // Rule 1: trying to change the owner through the form is ignored
                        .param("owner.id", other.getId().toString())
                        .param("ownerId", other.getId().toString()))
                .andExpect(redirectedUrl("/applications/" + id));

        JobApplication edited = apps.findById(id).orElseThrow();
        assertThat(edited.getCompany()).isEqualTo("Acme NZ");
        assertThat(edited.getLocation()).isEqualTo("Wellington");
        assertThat(edited.getOwner().getId()).isEqualTo(me.getId());
        assertThat(edited.getStatus()).isEqualTo(ApplicationStatus.SAVED);

        upload(me, id, DocumentType.CV, pdf("cv.pdf"));
        upload(me, id, DocumentType.COVER_LETTER, pdf("cl.pdf"));
        List<String> stored = documents.findStoredFilenamesByApplicationId(id);
        assertThat(stored).hasSize(2).allSatisfy(f -> assertThat(storage.exists(f)).isTrue());

        mvc.perform(post("/applications/{id}/delete", id).with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/applications"));

        assertThat(apps.existsById(id)).isFalse();
        assertThat(documents.findStoredFilenamesByApplicationId(id)).isEmpty();
        assertThat(timeline(id)).isEmpty();
        assertThat(stored).allSatisfy(f -> assertThat(storage.exists(f)).isFalse()); // rule 7
    }

    // ------------------------------------------------------------------ 4. Manual status updates keep the full history

    @Test
    void manualStatusUpdatesKeepFullHistory() throws Exception {
        AppUserDetails me = newUser("status");
        UUID id = createApplication(me, "Globex", "Junior Dev", null);
        LocalDateTime now = LocalDateTime.now();
        String nowStr = now.format(DT);

        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("toStatus", "APPLIED").param("occurredAt", nowStr).param("note", "Applied on SEEK"))
                .andExpect(redirectedUrl("/applications/" + id))
                .andExpect(flash().attribute("scrollTo", "status"))
                .andExpect(flash().attributeExists("success"));

        JobApplication app = apps.findById(id).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(app.getAppliedDate()).isEqualTo(now.toLocalDate());      // rule 9: applied date filled in
        List<StatusChange> t = timeline(id);
        assertThat(t).hasSize(2);
        assertThat(t.get(0).getFromStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(t.get(0).getToStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(t.get(0).getNote()).isEqualTo("Applied on SEEK");
        assertThat(t.get(0).getRecordedAt()).isAfterOrEqualTo(t.get(0).getOccurredAt());

        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("toStatus", "INTERVIEWING").param("occurredAt", nowStr))
                .andExpect(flash().attributeExists("success"));

        // Rule 3: backfill a past event (occurrence and entry times stored separately) without changing the current status
        LocalDateTime lastWeek = now.minusDays(7).withSecond(0).withNano(0);
        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("toStatus", "ASSESSMENT").param("occurredAt", lastWeek.format(DT))
                        .param("historyOnly", "true"))
                .andExpect(flash().attributeExists("success"));

        assertThat(apps.findById(id).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        t = timeline(id);
        assertThat(t).hasSize(4);
        assertThat(t).extracting(StatusChange::getToStatus).containsExactly(
                ApplicationStatus.INTERVIEWING, ApplicationStatus.APPLIED,
                ApplicationStatus.SAVED, ApplicationStatus.ASSESSMENT);
        StatusChange backfilled = t.get(3);
        assertThat(backfilled.getOccurredAt()).isEqualTo(lastWeek);
        assertThat(backfilled.getRecordedAt()).isAfter(lastWeek.plusDays(6));
        assertThat(backfilled.isBackfilled()).isTrue();

        // All of the following are rejected and write no history:
        // a time in the future
        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("toStatus", "OFFER").param("occurredAt", now.plusDays(3).format(DT)))
                .andExpect(flash().attributeExists("error"));
        // the same as the current status
        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("toStatus", "INTERVIEWING").param("occurredAt", nowStr))
                .andExpect(flash().attributeExists("error"));
        // no status chosen
        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("occurredAt", nowStr))
                .andExpect(flash().attributeExists("error"));
        // changing the current status with a time before the latest change (should tick "backfill only")
        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("toStatus", "REJECTED").param("occurredAt", now.minusDays(1).format(DT)))
                .andExpect(flash().attributeExists("error"));
        // "backfill only" ticked, but the time is not before the latest change
        mvc.perform(post("/applications/{id}/status", id).with(user(me)).with(csrf())
                        .param("toStatus", "REJECTED").param("occurredAt", nowStr).param("historyOnly", "true"))
                .andExpect(flash().attributeExists("error"));

        assertThat(timeline(id)).hasSize(4);
        assertThat(apps.findById(id).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    @Test
    void creatingAnAlreadySubmittedApplicationUsesTheAppliedDate() throws Exception {
        AppUserDetails me = newUser("late");
        java.time.LocalDate applied = java.time.LocalDate.now().minusDays(3);
        MvcResult r = mvc.perform(post("/applications").with(user(me)).with(csrf())
                        .param("company", "Umbrella").param("positionTitle", "Graduate")
                        .param("initialStatus", "APPLIED").param("appliedDate", applied.toString()))
                .andReturn();
        UUID id = UUID.fromString(r.getResponse().getRedirectedUrl().substring("/applications/".length()));
        StatusChange first = timeline(id).get(0);
        assertThat(first.getToStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(first.getOccurredAt()).isEqualTo(applied.atStartOfDay());
        assertThat(apps.findById(id).orElseThrow().getAppliedDate()).isEqualTo(applied);
    }

    // ------------------------------------------------------------------ 5. Multiple interview rounds

    @Test
    void userCanAddManyInterviewRounds_butNotDuplicateRoundNumbers() throws Exception {
        AppUserDetails me = newUser("iv");
        UUID id = createApplication(me, "Initech", "Graduate", null);

        for (int round = 1; round <= 4; round++) {     // rule 4: not limited to three rounds
            mvc.perform(post("/applications/{id}/interviews", id).with(user(me)).with(csrf())
                            .param("roundNumber", String.valueOf(round))
                            .param("interviewType", round == 1 ? "PHONE_SCREEN" : "TECHNICAL")
                            .param("outcome", "PASSED")
                            .param("scheduledAt", LocalDateTime.now().minusDays(10 - round).format(DT))
                            .param("notes", "Round " + round + " notes"))
                    .andExpect(flash().attributeExists("success"));
        }
        mvc.perform(post("/applications/{id}/interviews", id).with(user(me)).with(csrf())
                        .param("roundNumber", "2").param("interviewType", "HR").param("outcome", "PENDING"))
                .andExpect(flash().attributeExists("error"));

        List<Interview> list = interviews.findByApplicationIdOrderByRoundNumberAsc(id);
        assertThat(list).extracting(Interview::getRoundNumber).containsExactly(1, 2, 3, 4);
        assertThat(list.get(0).getNotes()).isEqualTo("Round 1 notes");

        // Editing cannot switch to an existing round number either
        mvc.perform(post("/applications/{a}/interviews/{i}", id, list.get(3).getId()).with(user(me)).with(csrf())
                        .param("roundNumber", "1").param("interviewType", "FINAL").param("outcome", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("interviewForm", "roundNumber"));

        mvc.perform(get("/applications/{id}", id).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("d"));
    }

    // ------------------------------------------------------------------ 6. Search, filter and sort

    @Test
    void listCanSearchFilterAndSort() throws Exception {
        AppUserDetails me = newUser("search");
        AppUserDetails stranger = newUser("stranger");
        UUID zeta = createApplication(me, "Zeta Corp", "Backend Engineer", null);
        UUID alpha = createApplication(me, "Alpha Ltd", "Frontend Developer", null);
        UUID beta = createApplication(me, "Beta Co", "Backend Developer", null);
        createApplication(stranger, "Alpha Other", "Backend Engineer", null);

        mvc.perform(post("/applications/{id}/status", alpha).with(user(me)).with(csrf())
                .param("toStatus", "APPLIED").param("occurredAt", LocalDateTime.now().format(DT)));

        // Filters are POSTed and kept in the session; the page itself is always the plain /applications
        MockHttpSession session = new MockHttpSession();
        List<JobApplication> r = listResult(postThenGet(me, session, "/applications/filter", "/applications",
                "company", "alp").andReturn());
        assertThat(r).extracting(JobApplication::getId).containsExactly(alpha);

        r = listResult(postThenGet(me, session, "/applications/filter", "/applications", "status", "APPLIED").andReturn());
        assertThat(r).extracting(JobApplication::getId).containsExactly(alpha);

        // Sorting by clicking the Company header: first click A-Z, second click Z-A; the filter is kept
        postThenGet(me, session, "/applications/filter", "/applications", "position", "backend");
        r = listResult(postThenGet(me, session, "/applications/sort", "/applications", "key", "company").andReturn());
        assertThat(r).extracting(JobApplication::getId).containsExactly(beta, zeta);
        r = listResult(postThenGet(me, session, "/applications/sort", "/applications", "key", "company").andReturn());
        assertThat(r).extracting(JobApplication::getId).containsExactly(zeta, beta);

        r = listResult(postThenGet(me, session, "/applications/filter", "/applications",
                "appliedFrom", java.time.LocalDate.now().toString()).andReturn());
        assertThat(r).extracting(JobApplication::getId).containsExactly(alpha);

        // Invalid values are ignored rather than failing; ownerId has no effect for regular users
        r = listResult(postThenGet(me, session, "/applications/filter", "/applications",
                "status", "NOT_A_STATUS", "ownerId", stranger.getId().toString()).andReturn());
        assertThat(r).extracting(JobApplication::getId).containsExactlyInAnyOrder(zeta, alpha, beta);
        mvc.perform(post("/applications/sort").with(user(me)).with(csrf()).session(session).param("key", "password"))
                .andExpect(redirectedUrl("/applications"));

        // Reset clears everything
        r = listResult(postThenGet(me, session, "/applications/reset", "/applications").andReturn());
        assertThat(r).hasSize(3);
    }

    // ------------------------------------------------------------------ 7. The dashboard only counts the user's own data

    @Test
    void dashboardShowsOnlyOwnStatistics() throws Exception {
        AppUserDetails a = newUser("dashA");
        AppUserDetails b = newUser("dashB");
        UUID a1 = createApplication(a, "A1", "Dev", null);
        createApplication(a, "A2", "Dev", null);
        UUID b1 = createApplication(b, "B1", "Dev", null);
        createApplication(b, "B2", "Dev", null);
        createApplication(b, "B3", "Dev", null);

        mvc.perform(post("/applications/{id}/status", a1).with(user(a)).with(csrf())
                .param("toStatus", "APPLIED").param("occurredAt", LocalDateTime.now().format(DT)));
        String soon = LocalDateTime.now().plusDays(2).format(DT);
        mvc.perform(post("/applications/{id}/interviews", a1).with(user(a)).with(csrf())
                .param("roundNumber", "1").param("interviewType", "VIDEO").param("outcome", "PENDING")
                .param("scheduledAt", soon));
        mvc.perform(post("/applications/{id}/interviews", b1).with(user(b)).with(csrf())
                .param("roundNumber", "1").param("interviewType", "VIDEO").param("outcome", "PENDING")
                .param("scheduledAt", soon));

        MvcResult r = mvc.perform(get("/dashboard").with(user(a))).andExpect(status().isOk()).andReturn();
        DashboardView view = (DashboardView) r.getModelAndView().getModel().get("view");
        assertThat(view.total()).isEqualTo(2);
        assertThat(view.countsByStatus().get(ApplicationStatus.APPLIED)).isEqualTo(1L);
        assertThat(view.countsByStatus().get(ApplicationStatus.SAVED)).isEqualTo(1L);
        assertThat(view.upcomingInterviews()).hasSize(1);
        assertThat(view.upcomingInterviews().get(0).getApplication().getId()).isEqualTo(a1);
        assertThat(view.recentApplications()).allSatisfy(x -> assertThat(x.getOwner().getId()).isEqualTo(a.getId()));
    }

    // ------------------------------------------------------------------ 8. Changing IDs in the URL cannot reach another user's data

    @Test
    void userCannotReachAnotherUsersDataByChangingIds() throws Exception {
        AppUserDetails alice = newUser("alice");
        AppUserDetails bob = newUser("bob");
        UUID aliceApp = createApplication(alice, "Secret Co", "Engineer", "confidential JD");
        UUID bobApp = createApplication(bob, "Bob Co", "Engineer", null);
        upload(alice, aliceApp, DocumentType.CV, pdf("alice-cv.pdf"));
        mvc.perform(post("/applications/{id}/interviews", aliceApp).with(user(alice)).with(csrf())
                .param("roundNumber", "1").param("interviewType", "HR").param("outcome", "PENDING"));
        UUID docId = documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(aliceApp).get(0).getId();
        UUID ivId = interviews.findByApplicationIdOrderByRoundNumberAsc(aliceApp).get(0).getId();

        mvc.perform(get("/applications/{id}", aliceApp).with(user(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/applications/{id}/edit", aliceApp).with(user(bob))).andExpect(status().isNotFound());
        mvc.perform(post("/applications/{id}", aliceApp).with(user(bob)).with(csrf())
                .param("company", "Hacked").param("positionTitle", "Hacked")).andExpect(status().isNotFound());
        mvc.perform(post("/applications/{id}/status", aliceApp).with(user(bob)).with(csrf())
                .param("toStatus", "REJECTED").param("occurredAt", LocalDateTime.now().format(DT)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/applications/{id}/interviews", aliceApp).with(user(bob)).with(csrf())
                .param("roundNumber", "9").param("interviewType", "HR").param("outcome", "PENDING"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/applications/{a}/interviews/{i}/edit", aliceApp, ivId).with(user(bob)))
                .andExpect(status().isNotFound());
        // Own application ID combined with someone else's interview ID
        mvc.perform(get("/applications/{a}/interviews/{i}/edit", bobApp, ivId).with(user(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/applications/{a}/interviews/{i}/delete", bobApp, ivId).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/applications/{id}/documents", aliceApp).file(pdf("evil.pdf"))
                .param("type", "CV").with(user(bob)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/documents/{id}/download", docId).with(user(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/documents/{id}/view", docId).with(user(bob))).andExpect(status().isNotFound());
        mvc.perform(post("/documents/{id}/delete", docId).with(user(bob)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/applications/{id}/delete", aliceApp).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());

        JobApplication still = apps.findById(aliceApp).orElseThrow();
        assertThat(still.getCompany()).isEqualTo("Secret Co");
        assertThat(still.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(documents.findById(docId)).isPresent();
        assertThat(interviews.findById(ivId)).isPresent();
        assertThat(interviews.findByApplicationIdOrderByRoundNumberAsc(aliceApp)).hasSize(1);

        // Regular users cannot open the admin pages
        mvc.perform(get("/admin/users").with(user(bob))).andExpect(status().isForbidden());

        // Admins can view any user's data, read-only
        AppUserDetails admin = newAdmin();
        mvc.perform(get("/applications/{id}", aliceApp).with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/documents/{id}/download", docId).with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/documents/{id}/view", docId).with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/users").with(user(admin))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 9. Invalid files, oversized files and invalid forms

    @Test
    void invalidFilesOversizedFilesAndInvalidFormsAreRejected() throws Exception {
        AppUserDetails me = newUser("val");
        UUID id = createApplication(me, "Valid Co", "Dev", null);
        long filesBefore = storedFileCount();

        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream",
                new byte[] {'M', 'Z', 1, 2, 3});
        MockMultipartFile fakePdf = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                new byte[] {'M', 'Z', 1, 2, 3});
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy("%PDF-1.4".getBytes(UTF_8), 0, big, 0, 8);
        MockMultipartFile tooBig = new MockMultipartFile("file", "big.pdf", "application/pdf", big);
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        // Only PDF is accepted: real Word and text files are rejected too
        MockMultipartFile docx = new MockMultipartFile("file", "cl.docx", "application/octet-stream",
                new byte[] {'P', 'K', 3, 4, 0, 0});
        MockMultipartFile txt = new MockMultipartFile("file", "notes.txt", "text/plain", "Hello JobTrack".getBytes(UTF_8));

        for (MockMultipartFile f : Arrays.asList(exe, fakePdf, tooBig, empty, docx, txt)) {
            mvc.perform(multipart("/applications/{id}/documents", id).file(f)
                            .param("type", "CV").with(user(me)).with(csrf()))
                    .andExpect(redirectedUrl("/applications/" + id))
                    .andExpect(flash().attribute("scrollTo", "documents"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(id)).isEmpty();
        assertThat(storedFileCount()).isEqualTo(filesBefore);

        // A valid PDF is accepted
        upload(me, id, DocumentType.COVER_LETTER, pdf("cl.pdf"));
        assertThat(documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(id)).hasSize(1);

        // Invalid forms
        mvc.perform(post("/applications").with(user(me)).with(csrf())
                        .param("company", "   ").param("positionTitle", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("applications/form"))
                .andExpect(model().attributeHasFieldErrors("form", "company", "positionTitle"));

        mvc.perform(post("/applications").with(user(me)).with(csrf())
                        .param("company", "X").param("positionTitle", "Y").param("jobUrl", "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "jobUrl"));

        mvc.perform(post("/applications").with(user(me)).with(csrf())
                        .param("company", "X").param("positionTitle", "Y")
                        .param("appliedDate", java.time.LocalDate.now().plusDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "appliedDate"));

        mvc.perform(post("/applications/{id}/interviews", id).with(user(me)).with(csrf())
                        .param("roundNumber", "0").param("interviewType", "HR").param("outcome", "PENDING"))
                .andExpect(flash().attributeExists("error"));
        assertThat(interviews.findByApplicationIdOrderByRoundNumberAsc(id)).isEmpty();

        // Submissions without a CSRF token are rejected
        mvc.perform(post("/applications/{id}/delete", id).with(user(me))).andExpect(status().isForbidden());
        assertThat(apps.existsById(id)).isTrue();
    }

    // ------------------------------------------------------------------ Admin

    @Test
    void adminCanDisableAndDeleteUsers_withFileCleanup() throws Exception {
        AppUserDetails admin = newAdmin();
        AppUserDetails victim = newUser("victim");
        UUID id = createApplication(victim, "Gone Co", "Dev", null);
        upload(victim, id, DocumentType.CV, pdf("cv.pdf"));
        String stored = documents.findStoredFilenamesByApplicationId(id).get(0);

        // After disabling, the user's existing session ends immediately
        mvc.perform(post("/admin/users/{id}/enabled", victim.getId()).param("enabled", "false")
                        .with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(get("/dashboard").with(user(victim))).andExpect(redirectedUrl("/login"));

        // Admins cannot delete or disable themselves
        mvc.perform(post("/admin/users/{id}/delete", admin.getId()).with(user(admin)).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertThat(userRepository.existsById(admin.getId())).isTrue();

        mvc.perform(post("/admin/users/{id}/delete", victim.getId()).with(user(admin)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(userRepository.existsById(victim.getId())).isFalse();
        assertThat(apps.existsById(id)).isFalse();
        assertThat(storage.exists(stored)).isFalse();
    }

    @Test
    void adminHasNoUserFeatures_andCannotChangeUsersData() throws Exception {
        AppUserDetails admin = newAdmin();
        AppUserDetails alice = newUser("alice");
        UUID appId = createApplication(alice, "Watched Co", "Engineer", null);
        upload(alice, appId, DocumentType.CV, pdf("cv.pdf"));
        UUID docId = documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(appId).get(0).getId();

        // Admins sign in to the admin overview; the personal dashboard redirects there
        mvc.perform(get("/dashboard").with(user(admin))).andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/").with(user(admin))).andExpect(redirectedUrl("/admin"));

        // No creating applications of their own
        mvc.perform(get("/applications/new").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/applications").with(user(admin)).with(csrf())
                .param("company", "Admin Co").param("positionTitle", "Admin")).andExpect(status().isForbidden());

        // No changing a user's application, status, interviews or documents
        mvc.perform(get("/applications/{id}/edit", appId).with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/applications/{id}", appId).with(user(admin)).with(csrf())
                .param("company", "Changed").param("positionTitle", "Changed")).andExpect(status().isForbidden());
        mvc.perform(post("/applications/{id}/status", appId).with(user(admin)).with(csrf())
                .param("toStatus", "REJECTED").param("occurredAt", LocalDateTime.now().format(DT)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/applications/{id}/interviews", appId).with(user(admin)).with(csrf())
                .param("roundNumber", "1").param("interviewType", "HR").param("outcome", "PENDING"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/applications/{id}/documents", appId).file(pdf("x.pdf"))
                .param("type", "CV").with(user(admin)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/documents/{id}/delete", docId).with(user(admin)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/applications/{id}/delete", appId).with(user(admin)).with(csrf())).andExpect(status().isForbidden());

        JobApplication still = apps.findById(appId).orElseThrow();
        assertThat(still.getCompany()).isEqualTo("Watched Co");
        assertThat(still.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(documents.findById(docId)).isPresent();
        assertThat(interviews.findByApplicationIdOrderByRoundNumberAsc(appId)).isEmpty();

        // Read-only views still work
        mvc.perform(get("/applications/{id}", appId).with(user(admin))).andExpect(status().isOk());
        postThenGet(admin, new MockHttpSession(), "/applications/filter", "/applications",
                "ownerId", alice.getId().toString());
    }

    @Test
    void adminCanMonitorUsers() throws Exception {
        AppUserDetails admin = newAdmin();
        AppUserDetails carol = newUser("carol");
        UUID appId = createApplication(carol, "Monitored Co", "Engineer", null);
        upload(carol, appId, DocumentType.CV, pdf("cv.pdf"));

        MvcResult overview = mvc.perform(get("/admin").with(user(admin)))
                .andExpect(status().isOk()).andExpect(view().name("admin/overview")).andReturn();
        com.jobtrack.service.AdminService.Overview o =
                (com.jobtrack.service.AdminService.Overview) overview.getModelAndView().getModel().get("o");
        assertThat(o.totalUsers()).isGreaterThanOrEqualTo(1);
        assertThat(o.totalApplications()).isGreaterThanOrEqualTo(1);
        assertThat(o.documentCount()).isGreaterThanOrEqualTo(1);

        MvcResult list = postThenGet(admin, new MockHttpSession(), "/admin/users/filter", "/admin/users",
                "q", carol.getUsername()).andReturn();
        @SuppressWarnings("unchecked")
        List<com.jobtrack.service.AdminService.UserRow> rows =
                (List<com.jobtrack.service.AdminService.UserRow>) list.getModelAndView().getModel().get("rows");
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.user().getId()).isEqualTo(carol.getId());
            assertThat(r.applicationCount()).isEqualTo(1);
        });

        MvcResult detail = mvc.perform(get("/admin/users/{id}", carol.getId()).with(user(admin)))
                .andExpect(status().isOk()).andExpect(view().name("admin/user-detail")).andReturn();
        com.jobtrack.service.AdminService.UserDetail d =
                (com.jobtrack.service.AdminService.UserDetail) detail.getModelAndView().getModel().get("u");
        assertThat(d.applicationCount()).isEqualTo(1);
        assertThat(d.documentCount()).isEqualTo(1);
        assertThat(d.applications()).extracting(JobApplication::getId).containsExactly(appId);

        // Admin accounts are not listed or monitored as users; regular users cannot reach the admin area
        mvc.perform(get("/admin/users/{id}", admin.getId()).with(user(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/admin").with(user(carol))).andExpect(status().isForbidden());
    }
}
