package com.aaemGdriveIntegration.core.workflows;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=List GDrive Files in Folder"
        }
)
public class UploadToGdrive implements WorkflowProcess {

    private static final Logger log = LoggerFactory.getLogger(UploadToGdrive.class);

    private static final String SUBSERVICE = "admin-service-user";

    private static final String GDRIVE_FOLDER_ID = "1vKuzDhIYQ7g_yCa2vBSKb4wJUYRQ-4tC";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {
        log.info("Starting GDrive file list workflow");

        Map<String, Object> authMap = new HashMap<>();
        authMap.put(ResourceResolverFactory.SUBSERVICE, SUBSERVICE);

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authMap)) {
            if(resolver == null) {
                log.info("Resolver is null");
            }

            InputStream keyInputStream = getKeyJsonStream(resolver);
            listFilesInGDriveFolder(keyInputStream, GDRIVE_FOLDER_ID);
        } catch (Exception e) {
            log.error("Error listing GDrive files", e);
            throw new WorkflowException("GDrive listing failed", e);
        }
    }

    private InputStream getKeyJsonStream(ResourceResolver resolver) throws Exception {
        String gdriveKey = "{}";

        return new ByteArrayInputStream(gdriveKey.getBytes());
    }

    private void listFilesInGDriveFolder(InputStream keyInputStream, String folderId) throws Exception {
        GoogleCredentials credentials = GoogleCredentials.fromStream(keyInputStream)
                .createScoped(Collections.singletonList(DriveScopes.DRIVE_READONLY));

        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        Drive driveService = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer
        ).setApplicationName("AEM-GDrive-Integration").build();

        String query = String.format("'%s' in parents and trashed = false", folderId);

        List<File> files = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name, mimeType)")
                .execute()
                .getFiles();

        if (files == null || files.isEmpty()) {
            log.info("No files found in GDrive folder: {}", folderId);
        } else {
            log.info("Files in GDrive folder ID: {}", folderId);
            for (File file : files) {
                log.info("{} (ID: {}) - {}", file.getName(), file.getId(), file.getMimeType());
            }
        }
    }

}
