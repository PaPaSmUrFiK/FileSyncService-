package minio

import (
	"context"
	"fmt"
	"net/url"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

type Client struct {
	minioClient *minio.Client
	bucket      string
}

func NewMinioClient(endpoint, accessKey, secretKey, bucket string, useSSL bool) (*Client, error) {
	const op = "storage.minio.NewMinioClient"

	client, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: useSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("%s: не удалось создать клиент minio: %w", op, err)
	}

	return &Client{
		minioClient: client,
		bucket:      bucket,
	}, nil
}

func (c *Client) GetPresignedUploadURL(ctx context.Context, objectName string, expires time.Duration) (*url.URL, error) {
	const op = "storage.minio.GetPresignedUploadURL"

	// MinIO requires the bucket to exist
	exists, err := c.minioClient.BucketExists(ctx, c.bucket)
	if err != nil {
		return nil, fmt.Errorf("%s: ошибка при проверке существования бакета: %w", op, err)
	}
	if !exists {
		err = c.minioClient.MakeBucket(ctx, c.bucket, minio.MakeBucketOptions{})
		if err != nil {
			return nil, fmt.Errorf("%s: не удалось создать бакет: %w", op, err)
		}
	}

	presignedURL, err := c.minioClient.PresignedPutObject(ctx, c.bucket, objectName, expires)
	if err != nil {
		return nil, fmt.Errorf("%s: не удалось сгенерировать presigned URL для загрузки: %w", op, err)
	}

	return presignedURL, nil
}

func (c *Client) GetPresignedDownloadURL(ctx context.Context, objectName string, expires time.Duration) (*url.URL, error) {
	const op = "storage.minio.GetPresignedDownloadURL"

	reqParams := make(url.Values)
	presignedURL, err := c.minioClient.PresignedGetObject(ctx, c.bucket, objectName, expires, reqParams)
	if err != nil {
		return nil, fmt.Errorf("%s: не удалось сгенерировать presigned URL для скачивания: %w", op, err)
	}

	return presignedURL, nil
}

func (c *Client) DeleteObject(ctx context.Context, objectName string) error {
	const op = "storage.minio.DeleteObject"

	err := c.minioClient.RemoveObject(ctx, c.bucket, objectName, minio.RemoveObjectOptions{})
	if err != nil {
		return fmt.Errorf("%s: не удалось удалить объект: %w", op, err)
	}
	return nil
}

func (c *Client) CopyObject(ctx context.Context, srcKey, destKey string) error {
	const op = "storage.minio.CopyObject"

	src := minio.CopySrcOptions{
		Bucket: c.bucket,
		Object: srcKey,
	}
	dst := minio.CopyDestOptions{
		Bucket: c.bucket,
		Object: destKey,
	}
	_, err := c.minioClient.CopyObject(ctx, dst, src)
	if err != nil {
		return fmt.Errorf("%s: не удалось скопировать объект: %w", op, err)
	}
	return nil
}
