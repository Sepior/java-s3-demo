package com.sepior.s3.demo;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.PropertiesFileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.sepior.s3.SepiorS3Encryption;
import com.sepior.sdk.SepiorServiceException;
import com.sepior.sdk.SepiorServicesClient;
import com.sepior.sdk.SepiorServicesClientConfiguration;
import com.sepior.sdk.SepiorUserException;
import com.sepior.sdk.SepiorUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class S3EncryptionDemo {

    private static final String sepiorServicesClientConfigurationFile = "sepior.config";
    private static final String amazonS3ConfigurationFile = "aws.config";
    private static final String encryptedAwsCredentials = "AQAAAAEAAAAAAAAAJAAAAGMwYmQ0MzZmLTZhMTMtNGZmNC1hMTk4LTY2ODQ5ZTFhYTc5YwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABTRVBDUllQVAAAAAAAAAAAAAAAAAAAAADPEn0NCRexpsN34dkPUrQ-CVYiT-L4sEssCZVa0gJx30kGm9A5Vcv63cthXMMhAPPUB3Xkh9Bbb9Qdy-0DE9VuDHy3jRI";

    private static SepiorS3Encryption s3Enc;

    private static void checkUsage(String[] args) {
        if (args.length != 4 || ((!args[0].equals("upload") && !args[0].equals("download")))) {
            System.out.println("Parameters: <upload|download> <s3Bucket> <s3Key> <filename>");
            System.exit(1);
        }
    }

    private static AWSCredentials getAwsCredentials(SepiorServicesClient sepiorClient) {
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
        AmazonS3 awsClient = s3Enc.getAmazonS3();
        PutObjectRequest put = s3Enc.getPutObjectRequest(s3Bucket, s3Key, new File(filename));
        awsClient.putObject(put);
    }

    private static void uploadUsingSepiorSDK(String s3Bucket, String s3Key, String filename) throws SepiorServiceException, SepiorUserException {
        s3Enc.uploadData(s3Bucket, s3Key, new File(filename));
    }

    private static void downloadUsingAmazonSDK(String s3Bucket, String s3Key, String filename) throws IOException {
        AmazonS3 awsClient = s3Enc.getAmazonS3();
        GetObjectRequest get = new GetObjectRequest(s3Bucket, s3Key);
        S3Object s3Object = awsClient.getObject(get);
        try (InputStream in = s3Object.getObjectContent()) {
            Files.copy(in, Paths.get(filename));
        }
    }

    private static void downloadUsingSepiorSDK(String s3Bucket, String s3Key, String filename) throws IOException {
        InputStream in = s3Enc.downloadData(s3Bucket, s3Key);
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

        /* Create Sepior S3 encryption object with the given configuration and get an AWS client. */
        s3Enc = new SepiorS3Encryption(sepiorClient, awsCredentials);

        if (command.equals("upload")) {
            /* Put filename to S3 (s3Bucket, s3Key). */

            //uploadUsingSepiorSDK(s3Bucket, s3Key, filename);
            uploadUsingAmazonSDK(s3Bucket, s3Key, filename);
        } else {
            /* Get (s3Bucket, s3Key) from S3 to filename. */

            //downloadUsingSepiorSDK(s3Bucket, s3Key, filename);
            downloadUsingAmazonSDK(s3Bucket, s3Key, filename);
        }
    }
}
