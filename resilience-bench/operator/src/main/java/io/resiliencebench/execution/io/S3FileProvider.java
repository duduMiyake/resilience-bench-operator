package io.resiliencebench.execution.io;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import com.amazonaws.services.s3.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.of;

@Component("s3FileProvider")
public class S3FileProvider implements FileProvider {

  private final static Logger logger = LoggerFactory.getLogger(S3FileProvider.class);

  private final AmazonS3 s3Client;
  private final String bucketName;
  private final String keyPrefix;

  public S3FileProvider(@Value("${AWS_BUCKET_NAME:none}") String bucketName,
                        @Value("${AWS_S3_PREFIX:}") String keyPrefix,
                        AmazonS3 s3Client) {
    this.bucketName = bucketName; // TODO abstrair para CRD Benchmark
    this.keyPrefix = normalizeKeyPrefix(keyPrefix);
    this.s3Client = s3Client;
  }

  @Override
  public void writeToFile(String resultFile, String content) {
    try {
      var contentBytes = content.getBytes(UTF_8);
      InputStream inputStream = new ByteArrayInputStream(contentBytes);
      var metadata = new ObjectMetadata();
      metadata.setContentLength(contentBytes.length);
      var putObjectRequest = new PutObjectRequest(bucketName, objectKey(resultFile), inputStream, metadata);
      s3Client.putObject(putObjectRequest);
      logger.info("File {} uploaded to bucket {}.", resultFile, bucketName);
    } catch (AmazonServiceException e) {
      logger.warn("Error writing file {}. Error code: {}. Message: {}", resultFile, e.getErrorCode(), e.getErrorMessage());
    } catch (Exception e) {
      logger.warn("Error writing file {}. {}", resultFile, e.getMessage());
    }
  }

  @Override
  public Optional<String> getFileAsString(String resultFile) {
    try {
      var content = s3Client.getObjectAsString(bucketName, objectKey(resultFile));
      if (content == null) {
        logger.warn("File {} not found in bucket {}.", resultFile, bucketName);
        return empty();
      }
      return of(content);
    } catch (AmazonServiceException e) {
      logger.warn("Error reading file {}. Error code: {}. Message: {}", resultFile, e.getErrorCode(), e.getErrorMessage());
      return empty();
    }
    catch (Exception e) {
      logger.warn("Error reading file {}. {}", resultFile, e.getMessage());
      return empty();
    }
  }

  private String objectKey(String resultFile) {
    var key = resultFile;
    while (key.startsWith("/")) {
      key = key.substring(1);
    }
    if (keyPrefix.isBlank()) {
      return key;
    }
    return keyPrefix + "/" + key;
  }

  private static String normalizeKeyPrefix(String keyPrefix) {
    if (keyPrefix == null || keyPrefix.isBlank()) {
      return "";
    }
    var normalizedPrefix = keyPrefix.trim();
    while (normalizedPrefix.startsWith("/")) {
      normalizedPrefix = normalizedPrefix.substring(1);
    }
    while (normalizedPrefix.endsWith("/")) {
      normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
    }
    return normalizedPrefix;
  }
}
