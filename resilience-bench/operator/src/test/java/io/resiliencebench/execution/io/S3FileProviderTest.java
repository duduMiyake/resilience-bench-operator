package io.resiliencebench.execution.io;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FileProviderTest {

  @Test
  void writeToFileNormalizesResultFileAsObjectKey() {
    var s3Client = mock(AmazonS3.class);
    var provider = new S3FileProvider("bucket", "/hipstershop/", s3Client);

    provider.writeToFile("/results/run-results.json", "{}");

    var requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture());
    assertEquals("bucket", requestCaptor.getValue().getBucketName());
    assertEquals("hipstershop/results/run-results.json", requestCaptor.getValue().getKey());
  }

  @Test
  void getFileAsStringNormalizesResultFileAsObjectKey() {
    var s3Client = mock(AmazonS3.class);
    var provider = new S3FileProvider("bucket", "hipstershop", s3Client);
    when(s3Client.getObjectAsString("bucket", "hipstershop/results/run-results.json")).thenReturn("{}");

    var result = provider.getFileAsString("/results/run-results.json");

    assertTrue(result.isPresent());
    assertEquals("{}", result.get());
  }
}
