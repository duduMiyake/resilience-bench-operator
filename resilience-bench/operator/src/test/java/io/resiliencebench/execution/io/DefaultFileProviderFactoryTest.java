package io.resiliencebench.execution.io;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;

class DefaultFileProviderFactoryTest {

  @Mock
  private S3FileProvider s3FileProvider;

  @Mock
  private Environment env;

  @Mock
  private LocalFileProvider localFileProvider;

  private DefaultFileProviderFactory factory;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    factory = new DefaultFileProviderFactory(s3FileProvider, localFileProvider, env);
  }

  @Test
  void testCreateWithCloudStorage() {
    when(env.getProperty("STORAGE_TYPE")).thenReturn("CLOUD");
    var result = factory.create();
    assertEquals(s3FileProvider, result);
  }

  @Test
  void testCreateWithCloudStorageIgnoringCase() {
    when(env.getProperty("STORAGE_TYPE")).thenReturn("cloud");
    var result = factory.create();
    assertEquals(s3FileProvider, result);
  }

  @Test
  void testCreateWithLocalStorage() {
    when(env.getProperty("STORAGE_TYPE")).thenReturn("LOCAL");
    var result = factory.create();
    assertEquals(localFileProvider, result);
  }

  @Test
  void testCreateWithLocalStorageIgnoringCase() {
    when(env.getProperty("STORAGE_TYPE")).thenReturn("local");
    var result = factory.create();
    assertEquals(localFileProvider, result);
  }

  @Test
  void testCreateWithEmptyStorageSpecified() {
    when(env.getProperty("STORAGE_TYPE")).thenReturn("");
    var result = factory.create();
    assertEquals(localFileProvider, result);
  }

  @Test
  void testCreateWithNullStorageSpecified() {
    when(env.getProperty("STORAGE")).thenReturn(null);
    var result = factory.create();
    assertEquals(localFileProvider, result);
  }
}
