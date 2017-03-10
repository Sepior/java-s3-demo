Sepior Java S3 Demo
===================

This is an example of how to use the the Sepior S3 Library to store and fetch encrypted data on Amazon S3. It works as a simple command line client.

Building the demo
-----------------

This demo uses maven. To build it simply run:

    mvn clean install

Credentials
-----------

Amazon and Sepior credentials must be provided in the following files in the working directory:

aws.config:
```bash
accessKey=AWS_ACCESS_KEY
secretKey=AWS_SECRET_KEY
```

sepior.config:
```javascript
{
    "userId": "KS_USER",
    "password": "KS_PASSWORD",
    "keyServerUris": [
      "https://KS1_URL",
      "https://KS2_URL",
      "https://KS3_URL"
    ],
    "applicationId": "APPLICATION_ID"
}
```

The Amazon user must have the proper access rights to the S3 buckets you want to use.

Usage
-----

To try the demo simply run the generated jar file:

```
java -jar target/java-s3-demo-0.0.0-SNAPSHOT-jar-with-dependencies.jar
```

Without any arguments it will print out usage information:

```
Parameters: <upload|download> <s3Bucket> <s3Key> <filename>
```

Examples
--------
Upload a file called `test.txt` to an S3 bucket called `myBucket` under an S3 key called `myKey`:

```
java -jar target/java-s3-demo-0.0.0-SNAPSHOT-jar-with-dependencies.jar upload myBucket mykey test.txt
```

Download the data stored in an S3 bucket called `myBucket` under an S3 key called `myKey` and place it in a file called `test2.txt`:

```
java -jar target/java-s3-demo-0.0.0-SNAPSHOT-jar-with-dependencies.jar download myBucket mykey test2.txt
```

Enabling Strong Encryption
--------------------------

The Sepior SDK uses strong encryption. If you are using Oracle's JRE you must make sure that the Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files are installed in `${java.home}/jre/lib/security/`. If this is not the case you will get the following exception:

```java
java.security.InvalidKeyException: Illegal key size
```

The policy files can be downloaded here:

Java 7: http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html
Java 8: http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html
