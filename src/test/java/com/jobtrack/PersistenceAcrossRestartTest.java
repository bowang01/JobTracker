package com.jobtrack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.repository.DocumentRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusChangeRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.DocumentService;
import com.jobtrack.service.FileStorageService;
import com.jobtrack.service.JobApplicationService;
import com.jobtrack.service.UserService;
import com.jobtrack.web.form.ApplicationForm;
import com.jobtrack.web.form.RegistrationForm;

/** Scenario 10: the database and files survive an application restart (file-based database + disk directory, app started twice). */
class PersistenceAcrossRestartTest {

    @TempDir
    Path dataDir;

    private String[] args() {
        String db = dataDir.resolve("db").resolve("jobtrack").toAbsolutePath().toString().replace('\\', '/');
        return new String[] {
            "--server.port=0",
            "--spring.datasource.url=jdbc:h2:file:" + db + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--jobtrack.storage.dir=" + dataDir.resolve("files").toAbsolutePath(),
            "--jobtrack.admin.password=AdminPass123",
            "--spring.devtools.restart.enabled=false"
        };
    }

    @Test
    void databaseAndFilesSurviveRestart() {
        UUID appId;
        String storedFilename;

        try (ConfigurableApplicationContext ctx = SpringApplication.run(JobTrackApplication.class, args())) {
            RegistrationForm reg = new RegistrationForm();
            reg.setEmail("persist@example.com");
            reg.setPassword("password123");
            reg.setConfirmPassword("password123");
            AppUserDetails me = new AppUserDetails(ctx.getBean(UserService.class).register(reg));

            ApplicationForm form = new ApplicationForm();
            form.setCompany("Durable Ltd");
            form.setPositionTitle("Graduate Engineer");
            form.setJdText("Persist me");
            JobApplication app = ctx.getBean(JobApplicationService.class).create(form, me);
            appId = app.getId();

            ApplicationDocument doc = ctx.getBean(DocumentService.class).upload(appId, DocumentType.CV,
                    new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 persist".getBytes(UTF_8)), me);
            storedFilename = doc.getStoredFilename();
        }

        try (ConfigurableApplicationContext ctx = SpringApplication.run(JobTrackApplication.class, args())) {
            assertThat(ctx.getBean(UserRepository.class).findByEmailIgnoreCase("persist@example.com")).isPresent();
            JobApplication app = ctx.getBean(JobApplicationRepository.class).findById(appId).orElseThrow();
            assertThat(app.getCompany()).isEqualTo("Durable Ltd");
            assertThat(app.getJdText()).isEqualTo("Persist me");
            assertThat(ctx.getBean(StatusChangeRepository.class).findTimeline(appId)).hasSize(1);

            List<String> files = ctx.getBean(DocumentRepository.class).findStoredFilenamesByApplicationId(appId);
            assertThat(files).containsExactly(storedFilename);
            assertThat(ctx.getBean(FileStorageService.class).exists(storedFilename)).isTrue();
            // The initial admin is only created on the first start
            assertThat(ctx.getBean(UserRepository.class).countByRole(com.jobtrack.domain.Role.ADMIN)).isEqualTo(1);
        }
    }
}
