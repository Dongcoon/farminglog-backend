package com.farmlog.export;

import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.entity.GeneratedFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.Mockito.*;

class ExportWorkerTest {
    @Test
    void staleCompletionDeletesPublishedPhysicalFile() {
        ExportJobCoordinator coordinator = mock(ExportJobCoordinator.class);
        ExportFileGenerator generator = mock(ExportFileGenerator.class);
        ExportStorage storage = mock(ExportStorage.class);
        ExportJobRow job = new ExportJobRow();
        job.setId(31L);
        GeneratedFile generated = new GeneratedFile("a.xlsx", "stored.xlsx", "type", 10L, Path.of("11/stored.xlsx"));
        when(coordinator.claim()).thenReturn(Optional.of(job));
        when(generator.generate(job)).thenReturn(generated);
        doThrow(new StaleExportClaimException(31L)).when(coordinator).complete(job, generated);

        new ExportWorker(coordinator, generator, storage).runNext();

        verify(storage).deleteQuietly(Path.of("11/stored.xlsx").toString());
        verify(coordinator, never()).failOrRetry(any(), anyString(), anyString(), anyBoolean());
    }
}
