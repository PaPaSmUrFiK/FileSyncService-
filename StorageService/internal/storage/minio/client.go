package minio

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strings"
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

	// Основной endpoint для браузера и подписи (localhost:9000)
	// Мы принудительно используем localhost, если endpoint содержит minio
	// Это гарантирует, что подпись и доступ идут через один адрес
	var publicEndpoint string
	if strings.Contains(endpoint, "minio") {
		publicEndpoint = "localhost:9000"
	} else {
		publicEndpoint = endpoint
	}

	// Кастомный транспорт для перенаправления localhost -> minio внутри Docker
	transport := &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			host, port, err := net.SplitHostPort(addr)
			if err != nil {
				return net.Dial(network, addr)
			}

			// Если мы пытаемся подключиться к localhost внутри контейнера,
			// перенаправляем на internal endpoint (minio:9000)
			if host == "localhost" || host == "127.0.0.1" || host == "::1" {
				fmt.Printf("DEBUG: Redirecting connection from %s to minio:%s\n", addr, port)
				d := net.Dialer{
					Timeout:   10 * time.Second,
					KeepAlive: 30 * time.Second,
				}
				conn, err := d.DialContext(ctx, network, "minio:"+port)
				if err != nil {
					fmt.Printf("DEBUG: Failed to dial minio:%s: %v\n", port, err)
					return nil, err
				}
				fmt.Printf("DEBUG: Successfully connected to minio:%s\n", port)
				return conn, nil
			}

			d := net.Dialer{
				Timeout:   30 * time.Second,
				KeepAlive: 30 * time.Second,
			}
			return d.DialContext(ctx, network, addr)
		},
		ForceAttemptHTTP2:     false,
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
	}

	client, err := minio.New(publicEndpoint, &minio.Options{
		Creds:     credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure:    useSSL,
		Transport: transport,
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
	fmt.Printf("DEBUG: Entering GetPresignedUploadURL for object: %s\n", objectName)

	fmt.Printf("DEBUG: Checking if bucket exists: %s\n", c.bucket)
	exists, err := c.minioClient.BucketExists(ctx, c.bucket)
	fmt.Printf("DEBUG: BucketExists result: exists=%v, err=%v\n", exists, err)

	if err != nil {
		return nil, fmt.Errorf("%s: ошибка при проверке существования бакета: %w", op, err)
	}
	if !exists {
		fmt.Printf("DEBUG: Creating bucket: %s\n", c.bucket)
		err = c.minioClient.MakeBucket(ctx, c.bucket, minio.MakeBucketOptions{})
		if err != nil {
			return nil, fmt.Errorf("%s: не удалось создать бакет: %w", op, err)
		}
	}

	// Генерируем URL - MinIO сам подставит нужный хост благодаря MINIO_DOMAIN
	fmt.Printf("DEBUG: Generating presigned URL...\n")
	presignedURL, err := c.minioClient.PresignedPutObject(ctx, c.bucket, objectName, expires)
	if err != nil {
		fmt.Printf("DEBUG: PresignedPutObject failed: %v\n", err)
		return nil, fmt.Errorf("%s: не удалось сгенерировать presigned URL: %w", op, err)
	}
	fmt.Printf("DEBUG: Successfully generated URL: %s\n", presignedURL.String())

	return presignedURL, nil
}

func (c *Client) GetPresignedDownloadURL(ctx context.Context, objectName string, fileName string, expires time.Duration) (*url.URL, error) {
	const op = "storage.minio.GetPresignedDownloadURL"

	reqParams := make(url.Values)
	if fileName != "" {
		// Set Content-Disposition to attachment with filename
		reqParams.Set("response-content-disposition", fmt.Sprintf("attachment; filename=\"%s\"", fileName))
	}

	presignedURL, err := c.minioClient.PresignedGetObject(ctx, c.bucket, objectName, expires, reqParams)
	if err != nil {
		return nil, fmt.Errorf("%s: не удалось сгенерировать presigned URL: %w", op, err)
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
