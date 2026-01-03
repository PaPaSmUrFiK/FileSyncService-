package com.fileservice.service;

import com.fileservice.model.File;
import com.fileservice.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для работы с папками
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FolderService {

    private final FileRepository fileRepository;
    private final FileService fileService;
    private final PermissionService permissionService;

    /**
     * Создание папки
     */
    public File createFolder(String name, String path, UUID userId, UUID parentFolderId) {
        log.debug("Creating folder: name={}, path={}, userId={}, parentFolderId={}",
                name, path, userId, parentFolderId);

        // Проверка на существование папки с таким же путем
        if (fileRepository.existsByPathAndUserIdAndIsDeletedFalse(path, userId)) {
            throw new IllegalArgumentException(
                    String.format("Folder with path '%s' already exists for user %s",
                            path, userId));
        }

        // Если указан родительский каталог, проверяем его существование
        File parentFolder = null;
        if (parentFolderId != null) {
            parentFolder = fileRepository.findByIdAndUserId(parentFolderId, userId)
                    .filter(f -> !f.isDeleted() && f.isFolder())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent folder does not exist or is not a folder"));
        }

        // Создаем папку
        File folder = File.builder()
                .name(name)
                .path(path)
                .parentFolder(parentFolder)
                .userId(userId)
                .isFolder(true)
                .size(0L)
                .build();

        File savedFolder = fileRepository.save(folder);
        log.info("Folder created: id={}, name={}, path={}, userId={}",
                savedFolder.getId(), savedFolder.getName(), savedFolder.getPath(), userId);
        return savedFolder;
    }

    /**
     * Получение содержимого папки
     */
    @Transactional(readOnly = true)
    public Page<File> listFolderContents(UUID folderId, UUID userId, Pageable pageable) {
        log.debug("Listing folder contents: folderId={}, userId={}, page={}, size={}",
                folderId, pageable.getPageNumber(), pageable.getPageSize());

        // Проверка существования папки и прав доступа
        fileRepository.findByIdAndUserId(folderId, userId)
                .filter(f -> !f.isDeleted() && f.isFolder())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Folder with id %s not found for user %s",
                                folderId, userId)));

        // Проверка прав доступа
        if (!permissionService.hasReadAccess(folderId, userId)) {
            throw new SecurityException(
                    String.format("User %s does not have read access to folder %s",
                            userId, folderId));
        }

        return fileRepository.findByUserIdAndParentFolderIdAndIsDeletedFalse(
                userId, folderId, pageable);
    }

    /**
     * Получение всех файлов в папке (без пагинации)
     */
    @Transactional(readOnly = true)
    public List<File> getAllFolderContents(UUID folderId, UUID userId) {
        log.debug("Getting all contents of folder: folderId={}, userId={}", folderId, userId);

        fileRepository.findByIdAndUserId(folderId, userId)
                .filter(f -> !f.isDeleted() && f.isFolder())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Folder with id %s not found for user %s",
                                folderId, userId)));

        return fileRepository.findByParentFolderIdAndIsDeletedFalse(folderId, Pageable.unpaged())
                .getContent();
    }

    /**
     * Перемещение файла/папки
     */
    public File moveFile(UUID fileId, UUID userId, UUID newParentFolderId) {
        log.debug("Moving file: fileId={}, userId={}, newParentFolderId={}",
                fileId, userId, newParentFolderId);

        File file = fileRepository.findByIdAndUserId(fileId, userId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("File with id %s not found for user %s", fileId, userId)));

        // Проверка прав на запись
        if (!permissionService.hasWriteAccess(fileId, userId)) {
            throw new SecurityException(
                    String.format("User %s does not have write access to file %s",
                            userId, fileId));
        }

        // Проверка нового родительского каталога
        File newParentFolder = null;
        if (newParentFolderId != null) {
            newParentFolder = fileRepository.findByIdAndUserId(newParentFolderId, userId)
                    .filter(f -> !f.isDeleted() && f.isFolder())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "New parent folder does not exist or is not a folder"));

            // Проверка прав на запись в новую папку
            if (!permissionService.hasWriteAccess(newParentFolderId, userId)) {
                throw new SecurityException(
                        String.format("User %s does not have write access to folder %s",
                                userId, newParentFolderId));
            }
        }

        // Проверка, что не перемещаем папку в саму себя или в подпапку
        if (file.isFolder() && newParentFolderId != null) {
            if (fileId.equals(newParentFolderId)) {
                throw new IllegalArgumentException("Cannot move folder into itself");
            }
            if (isDescendant(newParentFolderId, fileId)) {
                throw new IllegalArgumentException("Cannot move folder into its own descendant");
            }
        }

        // Обновляем путь файла
        String newPath = newParentFolder != null
                ? newParentFolder.getPath() + "/" + file.getName()
                : "/" + file.getName();

        // Проверка, что файл с таким путем не существует
        if (fileRepository.existsByPathAndUserIdAndIsDeletedFalse(newPath, userId)) {
            throw new IllegalArgumentException(
                    String.format("File with path '%s' already exists", newPath));
        }

        file.setParentFolder(newParentFolder);
        file.setPath(newPath);

        // Если это папка, обновляем пути всех дочерних элементов
        if (file.isFolder()) {
            updateChildrenPaths(file, newPath);
        }

        File savedFile = fileRepository.save(file);
        log.info("File moved: id={}, newParentFolderId={}, newPath={}",
                savedFile.getId(), newParentFolderId, newPath);
        return savedFile;
    }

    /**
     * Удаление папки (каскадное удаление всех файлов внутри)
     */
    public void deleteFolder(UUID folderId, UUID userId) {
        log.debug("Deleting folder: folderId={}, userId={}", folderId, userId);

        fileRepository.findByIdAndUserId(folderId, userId)
                .filter(f -> !f.isDeleted() && f.isFolder())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Folder with id %s not found for user %s",
                                folderId, userId)));

        // Проверка прав на удаление
        if (!permissionService.hasDeleteAccess(folderId, userId)) {
            throw new SecurityException(
                    String.format("User %s does not have delete access to folder %s",
                            userId, folderId));
        }

        // Получаем все дочерние элементы
        List<File> children = fileRepository
                .findByParentFolderIdAndIsDeletedFalse(folderId, Pageable.unpaged())
                .getContent();

        // Рекурсивно удаляем все дочерние элементы
        for (File child : children) {
            if (child.isFolder()) {
                deleteFolder(child.getId(), userId);
            } else {
                fileService.deleteFile(child.getId(), userId);
            }
        }

        // Удаляем саму папку
        fileService.deleteFile(folderId, userId);
        log.info("Folder deleted: id={}, userId={}, childrenCount={}",
                folderId, userId, children.size());
    }

    /**
     * Проверка, является ли папка потомком другой папки
     */
    @Transactional(readOnly = true)
    private boolean isDescendant(UUID ancestorId, UUID descendantId) {
        Optional<File> current = fileRepository.findById(descendantId);

        while (current.isPresent()) {
            File file = current.get();
            if (file.getParentFolder() == null) {
                return false;
            }
            if (file.getParentFolder().getId().equals(ancestorId)) {
                return true;
            }
            current = fileRepository.findById(file.getParentFolder().getId());
        }

        return false;
    }

    /**
     * Обновление путей всех дочерних элементов при перемещении папки
     */
    private void updateChildrenPaths(File folder, String newFolderPath) {
        List<File> children = fileRepository
                .findByParentFolderIdAndIsDeletedFalse(folder.getId(), Pageable.unpaged())
                .getContent();

        for (File child : children) {
            String newPath = newFolderPath + "/" + child.getName();
            child.setPath(newPath);
            fileRepository.save(child);

            // Рекурсивно обновляем пути для подпапок
            if (child.isFolder()) {
                updateChildrenPaths(child, newPath);
            }
        }
    }

    /**
     * Получение папки по ID
     */
    @Transactional(readOnly = true)
    public Optional<File> getFolder(UUID folderId, UUID userId) {
        return fileRepository.findByIdAndUserId(folderId, userId)
                .filter(f -> !f.isDeleted() && f.isFolder());
    }

    /**
     * Получение всех папок пользователя
     */
    @Transactional(readOnly = true)
    public List<File> getAllFolders(UUID userId) {
        return fileRepository.findByUserIdAndIsFolderTrueAndIsDeletedFalse(userId);
    }
}
