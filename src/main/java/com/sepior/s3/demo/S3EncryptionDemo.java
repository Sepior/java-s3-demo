package com.sepior.s3.demo;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.PropertiesFileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Encryption;
import com.amazonaws.services.s3.AmazonS3EncryptionClientBuilder;
import com.amazonaws.services.s3.model.CryptoConfiguration;
import com.amazonaws.services.s3.model.CryptoMode;
import com.amazonaws.services.s3.model.EncryptedPutObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.sepior.s3.SepiorEncryptionMaterialsProvider;
import com.sepior.s3.SepiorS3Encryption;
import com.sepior.sdk.SepiorServiceException;
import com.sepior.sdk.SepiorServicesClient;
import com.sepior.sdk.SepiorServicesClientConfiguration;
import com.sepior.sdk.SepiorUserException;
import com.sepior.sdk.SepiorUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Base64;

public class S3EncryptionDemo {

    private static final boolean useSepiorWrapper = false;

    private static final String sepiorServicesClientConfigurationFile = "sepior.config";
    private static final String amazonS3ConfigurationFile = "aws.config";
    private static final String encryptedAwsCredentials = "AQAAAAEAAAAAAAAAJAAAADA5M2QyZmI4LTBjMTUtNDdiMC1hZTc1LTBjY2VlZTE2NTNkZgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABTRVBDUllQVAAAAAAAAAAAAAAAAAAAAAC9on3ovwP8lVY_XwaP5vWIUC6bduS_EKw0t5tLZwEE1r2XhnXymHxrv_I-kch2aGdMcayR7kh4NGjcNwH7iKnfmz3ZuNE";

    private static SepiorS3Encryption sepiorS3Encryption;
    private static SepiorEncryptionMaterialsProvider sepiorEncryptionMaterialsProvider;
    private static AmazonS3Encryption s3EncryptionClient;

    private static void checkUsage(String[] args) {
        if (args.length != 4 || ((!args[0].equals("upload") && !args[0].equals("download")))) {
            System.out.println("Parameters: <upload|download> <s3Bucket> <s3Key> <filename>");
            System.exit(1);
        }
    }

    private static AWSCredentials getAwsCredentials(SepiorServicesClient sepiorClient) throws SepiorServiceException, SepiorUserException {
        File awsConfigFile = new File(amazonS3ConfigurationFile);
        if (awsConfigFile.exists() && !awsConfigFile.isDirectory()) {
            return new PropertiesFileCredentialsProvider(amazonS3ConfigurationFile).getCredentials();
        }

        try {
            try (InputStream decryptionStream = sepiorClient.getDecryptingInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(encryptedAwsCredentials)))) {
                byte[] decrypted = new byte[64];
                int length = decryptionStream.read(decrypted);
                String[] awsCredentials = new String(decrypted, 0, length).split(":");
                if (awsCredentials.length != 2) {
                    throw new RuntimeException("Invalid AWS credentials");
                }
                return new BasicAWSCredentials(awsCredentials[0], awsCredentials[1]);
            }
        } catch (SepiorServiceException | SepiorUserException | IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static void uploadUsingAmazonSDK(String s3Bucket, String s3Key, String filename) throws SepiorServiceException, SepiorUserException {
        /* Generate EncryptedPutObjectRequest. */
        EncryptedPutObjectRequest put = new EncryptedPutObjectRequest(s3Bucket, s3Key, new File(filename))
            .withMaterialsDescription(sepiorEncryptionMaterialsProvider.getMaterialDescription(s3Bucket, s3Key));

        /* Upload object to S3. */
        s3EncryptionClient.putObject(put);
    }

    private static void uploadUsingSepiorSDK(String s3Bucket, String s3Key, String filename) throws SepiorServiceException, SepiorUserException {
        sepiorS3Encryption.uploadData(s3Bucket, s3Key, new File(filename));
    }

    private static void downloadUsingAmazonSDK(String s3Bucket, String s3Key, String filename) throws IOException {
        /* Generate GetObjectRequest. */
        GetObjectRequest get = new GetObjectRequest(s3Bucket, s3Key);

        /* Get object from S3. */
        S3Object s3Object = s3EncryptionClient.getObject(get);

        try (InputStream in = s3Object.getObjectContent(); OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            int bytesRead;
            byte[] buffer = new byte[16384];
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void downloadUsingSepiorSDK(String s3Bucket, String s3Key, String filename) throws IOException {
        InputStream in = sepiorS3Encryption.downloadData(s3Bucket, s3Key);
        File file = new File(filename);
        try (OutputStream out = new FileOutputStream(file)) {
            IOUtils.copy(in, out);
        }
    }

    public static void main(String[] args) throws IOException, SepiorServiceException, SepiorUserException {
        checkUsage(args);

        /* Get program options. */
        String command = args[0];
        String s3Bucket = args[1];
        String s3Key = args[2];
        String filename = args[3];

        /* Initialise Sepior and AWS clients. */
        SepiorServicesClientConfiguration sepiorConfig = SepiorUtils.getConfigurationFromFile(Paths.get(sepiorServicesClientConfigurationFile));
        SepiorServicesClient sepiorClient = SepiorUtils.getSepiorServicesClient(sepiorConfig);
        AWSCredentials awsCredentials = getAwsCredentials(sepiorClient);

        /* Get Amazon S3 client. */
        sepiorEncryptionMaterialsProvider = new SepiorEncryptionMaterialsProvider(sepiorClient);
        s3EncryptionClient = AmazonS3EncryptionClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
            .withCryptoConfiguration(new CryptoConfiguration().withCryptoMode(CryptoMode.EncryptionOnly))
            .withEncryptionMaterials(sepiorEncryptionMaterialsProvider)
            .build();

        /* Get Sepior S3 client. */
        sepiorS3Encryption = new SepiorS3Encryption(sepiorClient, awsCredentials);

        if (command.equals("upload")) {
            /* Put filename to S3 (s3Bucket, s3Key). */
            if (useSepiorWrapper) {
                uploadUsingSepiorSDK(s3Bucket, s3Key, filename);
            } else {
                uploadUsingAmazonSDK(s3Bucket, s3Key, filename);
            }
        } else {
            /* Get (s3Bucket, s3Key) from S3 to filename. */
            if (useSepiorWrapper) {
                downloadUsingSepiorSDK(s3Bucket, s3Key, filename);
            } else {
                downloadUsingAmazonSDK(s3Bucket, s3Key, filename);
            }
        }
    }
}
