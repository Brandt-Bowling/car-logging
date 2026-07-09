import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:google_sign_in/google_sign_in.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:uuid/uuid.dart';

import '../models/car.dart';
import '../models/maintenance_record.dart';
import '../services/storage_service.dart';
import '../services/google_drive_service.dart';
import '../services/ai_parsing_service.dart';

class GoogleDriveSyncPage extends StatefulWidget {
  final bool isTab;
  const GoogleDriveSyncPage({super.key, this.isTab = false});

  @override
  State<GoogleDriveSyncPage> createState() => _GoogleDriveSyncPageState();
}

class _GoogleDriveSyncPageState extends State<GoogleDriveSyncPage> {
  GoogleSignInAccount? _currentUser;
  bool _isSigningIn = false;
  bool _isLoadingFolders = false;
  bool _isScanningFiles = false;

  List<drive.File> _folders = [];
  List<drive.File> _driveFiles = [];
  Set<String> _importedFileIds = {};
  List<Car> _cars = [];
  final Set<String> _selectedFileIds = {};

  String? _selectedFolderId;
  String? _selectedFolderName;
  String? _apiKey;

  @override
  void initState() {
    super.initState();
    _currentUser = GoogleDriveService.currentUser;
    _importedFileIds = StorageService.getImportedFileIds();
    _selectedFolderId = StorageService.getSyncFolderId();
    _selectedFolderName = StorageService.getSyncFolderName();
    _apiKey = StorageService.getGeminiApiKey();
    _cars = StorageService.getCars();

    // Listen to Google Sign-In status changes
    GoogleDriveService.onCurrentUserChanged.listen((
      GoogleSignInAccount? account,
    ) {
      if (mounted) {
        setState(() {
          _currentUser = account;
        });
        if (account != null) {
          _onAuthenticated();
        } else {
          setState(() {
            _folders = [];
            _driveFiles = [];
            _selectedFileIds.clear();
          });
        }
      }
    });

    // Check if already signed in silently
    _checkSignInSilently();
  }

  Future<void> _checkSignInSilently() async {
    try {
      final account = await GoogleDriveService.signIn();
      if (account != null) {
        setState(() {
          _currentUser = account;
        });
        _onAuthenticated();
      }
    } catch (e) {
      print('Silent sign-in failed: $e');
    }
  }

  Future<void> _onAuthenticated() async {
    await _loadFolders();
    if (_selectedFolderId != null) {
      _scanFolderFiles();
    }
  }

  Future<void> _handleSignIn() async {
    setState(() {
      _isSigningIn = true;
    });
    try {
      final account = await GoogleDriveService.signIn();
      if (account != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Connected as ${account.email}'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Sign-in failed: $e'),
          backgroundColor: Theme.of(context).colorScheme.error,
          behavior: SnackBarBehavior.floating,
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          _isSigningIn = false;
        });
      }
    }
  }

  Future<void> _handleSignOut() async {
    try {
      await GoogleDriveService.signOut();
      await StorageService.saveSyncFolderId(null);
      await StorageService.saveSyncFolderName(null);
      setState(() {
        _selectedFolderId = null;
        _selectedFolderName = null;
        _driveFiles = [];
        _folders = [];
        _selectedFileIds.clear();
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Disconnected Google account'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    } catch (e) {
      print('Sign-out error: $e');
    }
  }

  Future<void> _loadFolders() async {
    if (!mounted) return;
    setState(() {
      _isLoadingFolders = true;
    });

    final folders = await GoogleDriveService.listFolders();

    if (mounted) {
      setState(() {
        _folders = folders;
        _isLoadingFolders = false;
      });

      // Try auto-selecting a folder named 'Car Maintenance' if nothing is selected
      if (_selectedFolderId == null && folders.isNotEmpty) {
        final autoMatch = folders.firstWhere(
          (f) => f.name?.toLowerCase().contains('car maintenance') ?? false,
          orElse: () => folders.firstWhere(
            (f) => f.name?.toLowerCase().contains('maintenance') ?? false,
            orElse: () => drive.File(),
          ),
        );
        if (autoMatch.id != null) {
          _selectFolder(autoMatch.id!, autoMatch.name ?? 'Drive Folder');
        }
      }
    }
  }

  Future<void> _selectFolder(String folderId, String folderName) async {
    setState(() {
      _selectedFolderId = folderId;
      _selectedFolderName = folderName;
      _driveFiles = [];
      _selectedFileIds.clear();
    });
    await StorageService.saveSyncFolderId(folderId);
    await StorageService.saveSyncFolderName(folderName);
    _scanFolderFiles();
  }

  Future<void> _scanFolderFiles() async {
    if (_selectedFolderId == null) return;
    setState(() {
      _isScanningFiles = true;
    });

    final files = await GoogleDriveService.listFiles(_selectedFolderId!);

    if (mounted) {
      setState(() {
        _driveFiles = files;
        _importedFileIds = StorageService.getImportedFileIds();
        _selectedFileIds.clear();
        _isScanningFiles = false;
      });
    }
  }

  void _configureApiKey() {
    _apiKeyController.text = _apiKey ?? '';
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Configure Gemini AI'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Enter your Gemini API Key to enable automated receipt parsing. If no key is provided, the app will parse dates, odometer readings, and costs offline using the receipt filenames.',
              style: TextStyle(fontSize: 13),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _apiKeyController,
              decoration: const InputDecoration(
                labelText: 'Gemini API Key',
                border: OutlineInputBorder(),
                hintText: 'AIzaSy...',
              ),
              obscureText: true,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              final newKey = _apiKeyController.text.trim();
              await StorageService.saveGeminiApiKey(newKey);
              setState(() {
                _apiKey = newKey.isEmpty ? null : newKey;
              });
              if (!mounted) return;
              Navigator.pop(context);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    newKey.isEmpty
                        ? 'Gemini API Key removed'
                        : 'Gemini API Key updated!',
                  ),
                  behavior: SnackBarBehavior.floating,
                ),
              );
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  final TextEditingController _apiKeyController = TextEditingController();

  Future<void> _updateCarOdometerIfNeeded(String carId, int odometer) async {
    final carIndex = _cars.indexWhere((c) => c.id == carId);
    if (carIndex != -1) {
      final car = _cars[carIndex];
      if (car.odometer == null || odometer > car.odometer!) {
        final updatedCar = car.copyWith(odometer: odometer);
        final updatedCars = List<Car>.from(_cars);
        updatedCars[carIndex] = updatedCar;
        await StorageService.saveCars(updatedCars);
        if (mounted) {
          setState(() {
            _cars = updatedCars;
          });
        }
      }
    }
  }

  Future<void> _openBulkImportDialog(List<drive.File> files) async {
    if (_cars.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please add a vehicle first.')),
      );
      return;
    }

    String? selectedCarId = _cars.first.id;
    bool isProcessing = false;
    double progress = 0.0;
    String currentFileName = '';
    int currentFileIndex = 0;

    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            if (isProcessing) {
              return AlertDialog(
                title: const Text('Bulk Importing Receipts'),
                content: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    LinearProgressIndicator(value: progress),
                    const SizedBox(height: 16),
                    Text(
                      'Processing file ${currentFileIndex + 1} of ${files.length}',
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      currentFileName,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: Theme.of(context).colorScheme.outline,
                          ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              );
            }

            return AlertDialog(
              title: Text('Bulk Import (${files.length} files)'),
              content: SizedBox(
                width: double.maxFinite,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    DropdownButtonFormField<String>(
                      initialValue: selectedCarId,
                      decoration: const InputDecoration(
                        labelText: 'Assign all to Car',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.directions_car),
                      ),
                      items: _cars.map((car) {
                        return DropdownMenuItem<String>(
                          value: car.id,
                          child: Text('${car.year} ${car.make} ${car.model}'),
                        );
                      }).toList(),
                      onChanged: (val) {
                        setDialogState(() {
                          selectedCarId = val;
                        });
                      },
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Files to import:',
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 8),
                    Flexible(
                      child: ListView.builder(
                        shrinkWrap: true,
                        itemCount: files.length,
                        itemBuilder: (context, index) {
                          final file = files[index];
                          return ListTile(
                            dense: true,
                            contentPadding: EdgeInsets.zero,
                            leading: Icon(
                              _getFileTypeIcon(file.mimeType),
                              size: 20,
                            ),
                            title: Text(
                              file.name ?? 'Unnamed File',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: selectedCarId == null
                      ? null
                      : () async {
                          setDialogState(() {
                            isProcessing = true;
                          });

                          final apiKey = StorageService.getGeminiApiKey();
                          final isGeminiActive = apiKey != null && apiKey.isNotEmpty;
                          int successCount = 0;

                          for (int i = 0; i < files.length; i++) {
                            final file = files[i];
                            setDialogState(() {
                              currentFileIndex = i;
                              currentFileName = file.name ?? 'Unknown';
                              progress = i / files.length;
                            });

                            try {
                              Uint8List? fileBytes;
                              if (isGeminiActive && file.id != null) {
                                fileBytes = await GoogleDriveService.downloadFile(file.id!);
                              }

                              final extractedData = await AiParsingService.parseReceipt(
                                fileName: file.name ?? 'receipt.pdf',
                                mimeType: file.mimeType ?? 'application/pdf',
                                fileBytes: fileBytes,
                              );

                              final double? cost = double.tryParse(extractedData['cost']?.toString() ?? '');
                              final int odometer = int.tryParse(extractedData['odometer']?.toString() ?? '') ?? 0;
                              final DateTime date = DateTime.tryParse(extractedData['date']?.toString() ?? '') ?? DateTime.now();

                              final record = MaintenanceRecord(
                                id: const Uuid().v4(),
                                carId: selectedCarId!,
                                title: extractedData['title']?.toString().trim() ?? 'Maintenance Record',
                                date: date,
                                odometer: odometer,
                                cost: cost,
                                description: extractedData['description']?.toString().trim() ??
                                    'Imported in bulk from file: ${file.name}',
                                driveFileId: file.id,
                              );

                              await StorageService.addMaintenanceRecord(record);
                              await StorageService.markFileAsImported(file.id!);
                              await _updateCarOdometerIfNeeded(selectedCarId!, odometer);
                              
                              successCount++;
                            } catch (e) {
                              print('Failed to import ${file.name}: $e');
                            }
                          }

                          if (mounted) {
                            setState(() {
                              _importedFileIds = StorageService.getImportedFileIds();
                              _selectedFileIds.removeAll(files.map((f) => f.id).whereType<String>());
                            });
                          }

                          if (context.mounted) {
                            Navigator.pop(dialogContext); // Close dialog

                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text('Successfully imported $successCount of ${files.length} receipts!'),
                                behavior: SnackBarBehavior.floating,
                              ),
                            );
                          }
                        },
                  child: const Text('Start Import'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _buildBulkActionRow(ThemeData theme) {
    final newFiles = _driveFiles.where((f) => !_importedFileIds.contains(f.id)).toList();
    if (newFiles.isEmpty) return const SizedBox.shrink();

    final allSelected = newFiles.isNotEmpty &&
        newFiles.every((f) => _selectedFileIds.contains(f.id));
    final someSelected = newFiles.any((f) => _selectedFileIds.contains(f.id)) && !allSelected;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4.0),
      child: Row(
        children: [
          Checkbox(
            value: allSelected ? true : (someSelected ? null : false),
            tristate: true,
            onChanged: (val) {
              setState(() {
                if (val == true) {
                  for (final f in newFiles) {
                    if (f.id != null) _selectedFileIds.add(f.id!);
                  }
                } else {
                  _selectedFileIds.clear();
                }
              });
            },
          ),
          Text(
            'Select All New',
            style: theme.textTheme.bodyMedium,
          ),
          const Spacer(),
          FilledButton.icon(
            onPressed: _selectedFileIds.isEmpty
                ? null
                : () => _openBulkImportDialog(
                      newFiles.where((f) => _selectedFileIds.contains(f.id)).toList(),
                    ),
            icon: const Icon(Icons.playlist_add),
            label: Text('Bulk Import (${_selectedFileIds.length})'),
          ),
        ],
      ),
    );
  }

  Future<void> _openImportReviewSheet(drive.File file) async {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _ImportReviewModal(
        file: file,
        cars: _cars,
        onImportSuccess: (record) async {
          await StorageService.addMaintenanceRecord(record);
          await StorageService.markFileAsImported(file.id!);
          await _updateCarOdometerIfNeeded(record.carId, record.odometer);
          setState(() {
            _importedFileIds.add(file.id!);
            _selectedFileIds.remove(file.id!);
          });
          if (!mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Imported "${record.title}" into history!'),
              behavior: SnackBarBehavior.floating,
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Google Drive Sync'),
        automaticallyImplyLeading: !widget.isTab,
        actions: [
          if (_currentUser != null)
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Scan Folder',
              onPressed: _isScanningFiles ? null : _scanFolderFiles,
            ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // Connection Card
          _buildConnectionCard(theme),
          const SizedBox(height: 16),

          if (_currentUser != null) ...[
            // Gemini API Config Card
            _buildGeminiCard(theme),
            const SizedBox(height: 16),

            // Folder Configuration Section
            _buildFolderSelectorCard(theme),
            const SizedBox(height: 16),

            // Files Listing Header
            if (_selectedFolderId != null) ...[
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Files in Folder', style: theme.textTheme.titleMedium),
                  if (_isScanningFiles)
                    const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  else
                    Text(
                      '${_driveFiles.length} files found',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.outline,
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              if (_driveFiles.isNotEmpty) ...[
                _buildBulkActionRow(theme),
                const SizedBox(height: 8),
              ],
              _buildFilesList(theme),
            ],
          ],
        ],
      ),
    );
  }

  Widget _buildConnectionCard(ThemeData theme) {
    final isConnected = _currentUser != null;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: isConnected
                      ? theme.colorScheme.primaryContainer
                      : theme.colorScheme.surfaceContainerHighest,
                  backgroundImage: isConnected && _currentUser!.photoUrl != null
                      ? NetworkImage(_currentUser!.photoUrl!)
                      : null,
                  child: isConnected && _currentUser!.photoUrl == null
                      ? Text(
                          _currentUser!.displayName?[0].toUpperCase() ?? 'G',
                          style: const TextStyle(fontSize: 24),
                        )
                      : (!isConnected
                            ? const Icon(Icons.cloud_off, size: 28)
                            : null),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isConnected
                            ? (_currentUser!.displayName ??
                                  'Google Drive Connected')
                            : 'Sync to Google Drive',
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        isConnected
                            ? _currentUser!.email
                            : 'Upload service receipts & documents to a Drive folder to sync them dynamically.',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.outline,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (_isSigningIn)
              const Center(child: CircularProgressIndicator())
            else
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  if (isConnected)
                    TextButton.icon(
                      onPressed: _handleSignOut,
                      icon: const Icon(Icons.exit_to_app),
                      label: const Text('Disconnect'),
                    )
                  else
                    FilledButton.icon(
                      onPressed: _handleSignIn,
                      icon: const Icon(Icons.login),
                      label: const Text('Connect Google Drive'),
                    ),
                ],
              ),
          ],
        ),
      ).animate().fade().slideY(begin: 0.1, duration: 300.ms),
    );
  }

  Widget _buildGeminiCard(ThemeData theme) {
    final hasKey = _apiKey != null && _apiKey!.isNotEmpty;

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: ListTile(
        leading: Icon(
          hasKey ? Icons.auto_awesome : Icons.auto_awesome_outlined,
          color: hasKey ? Colors.amber : theme.colorScheme.outline,
          size: 28,
        ),
        title: const Text('Receipt Scanning (Gemini AI)'),
        subtitle: Text(
          hasKey
              ? 'Active (Gemini 1.5 Flash)'
              : 'Inactive (Filename smart regex fallback active)',
          style: theme.textTheme.bodySmall?.copyWith(
            color: hasKey
                ? theme.colorScheme.primary
                : theme.colorScheme.outline,
            fontWeight: hasKey ? FontWeight.bold : null,
          ),
        ),
        trailing: FilledButton.tonal(
          onPressed: _configureApiKey,
          child: Text(hasKey ? 'Configure' : 'Setup Key'),
        ),
      ),
    );
  }

  Widget _buildFolderSelectorCard(ThemeData theme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Sync Folder Source', style: theme.textTheme.titleSmall),
            const SizedBox(height: 12),
            if (_isLoadingFolders)
              const Center(child: LinearProgressIndicator())
            else if (_folders.isEmpty)
              Text(
                'No folders found in Google Drive. Please create a folder like "Car Maintenance" in your Drive first.',
                style: TextStyle(color: theme.colorScheme.error, fontSize: 13),
              )
            else
              DropdownButtonFormField<String>(
                initialValue: _selectedFolderId,
                hint: const Text('Choose a folder...'),
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  contentPadding: EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                ),
                items: _folders.map((folder) {
                  return DropdownMenuItem<String>(
                    value: folder.id,
                    child: Row(
                      children: [
                        const Icon(Icons.folder, color: Colors.amber, size: 20),
                        const SizedBox(width: 8),
                        Text(folder.name ?? 'Unnamed Folder'),
                      ],
                    ),
                  );
                }).toList(),
                onChanged: (id) {
                  if (id != null) {
                    final folder = _folders.firstWhere((f) => f.id == id);
                    _selectFolder(id, folder.name ?? 'Drive Folder');
                  }
                },
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildFilesList(ThemeData theme) {
    if (_driveFiles.isEmpty) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(32.0),
          child: Center(
            child: Column(
              children: [
                Icon(
                  Icons.folder_open,
                  size: 48,
                  color: theme.colorScheme.outline,
                ),
                const SizedBox(height: 12),
                const Text('Selected folder is empty.'),
                const SizedBox(height: 4),
                Text(
                  'Upload receipt images or PDFs to "${_selectedFolderName ?? 'your folder'}" in Drive and click refresh.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.outline,
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Card(
      clipBehavior: Clip.antiAlias,
      child: ListView.separated(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _driveFiles.length,
        separatorBuilder: (context, index) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final file = _driveFiles[index];
          final isImported = _importedFileIds.contains(file.id);
          final fileTypeIcon = _getFileTypeIcon(file.mimeType);

          return ListTile(
            leading: isImported
                ? Icon(
                    fileTypeIcon,
                    color: theme.colorScheme.outline,
                  )
                : Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Checkbox(
                        value: _selectedFileIds.contains(file.id),
                        visualDensity: VisualDensity.compact,
                        onChanged: (checked) {
                          setState(() {
                            if (checked == true) {
                              _selectedFileIds.add(file.id!);
                            } else {
                              _selectedFileIds.remove(file.id!);
                            }
                          });
                        },
                      ),
                      Icon(
                        fileTypeIcon,
                        color: theme.colorScheme.primary,
                      ),
                    ],
                  ),
            title: Text(
              file.name ?? 'Unnamed File',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: isImported ? theme.colorScheme.outline : null,
                decoration: isImported ? TextDecoration.lineThrough : null,
              ),
            ),
            subtitle: Text(
              'Size: ${_formatBytes(file.size)} • Created: ${_formatDate(file.createdTime)}',
              style: theme.textTheme.bodySmall,
            ),
            trailing: isImported
                ? Icon(Icons.check_circle, color: theme.colorScheme.primary)
                : Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      'New',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.onPrimaryContainer,
                      ),
                    ),
                  ),
            onTap: isImported ? null : () => _openImportReviewSheet(file),
          );
        },
      ),
    );
  }

  IconData _getFileTypeIcon(String? mimeType) {
    if (mimeType == null) return Icons.insert_drive_file;
    if (mimeType.startsWith('image/')) return Icons.image;
    if (mimeType == 'application/pdf') return Icons.picture_as_pdf;
    if (mimeType.contains('word') || mimeType.contains('document')) {
      return Icons.description;
    }
    return Icons.insert_drive_file;
  }

  String _formatBytes(String? sizeStr) {
    if (sizeStr == null) return 'Unknown size';
    final size = int.tryParse(sizeStr);
    if (size == null) return 'Unknown size';
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  String _formatDate(DateTime? dateTime) {
    if (dateTime == null) return '';
    return dateTime.toLocal().toString().split(' ')[0];
  }
}

// Interactive Review and Import Modal widget
class _ImportReviewModal extends StatefulWidget {
  final drive.File file;
  final List<Car> cars;
  final Function(MaintenanceRecord) onImportSuccess;

  const _ImportReviewModal({
    required this.file,
    required this.cars,
    required this.onImportSuccess,
  });

  @override
  State<_ImportReviewModal> createState() => _ImportReviewModalState();
}

class _ImportReviewModalState extends State<_ImportReviewModal> {
  bool _isLoading = true;
  String? _errorMessage;

  // Form controllers
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _dateController = TextEditingController();
  final TextEditingController _odometerController = TextEditingController();
  final TextEditingController _costController = TextEditingController();
  final TextEditingController _descController = TextEditingController();
  String? _selectedCarId;

  @override
  void initState() {
    super.initState();
    if (widget.cars.isNotEmpty) {
      _selectedCarId = widget.cars.first.id;
    }
    _runParsingWorkflow();
  }

  Future<void> _runParsingWorkflow() async {
    try {
      Uint8List? fileBytes;
      final apiKey = StorageService.getGeminiApiKey();

      // Only download bytes if Gemini key is available. If using regex fallback, we just parse the filename.
      final isGeminiActive = apiKey != null && apiKey.isNotEmpty;

      if (isGeminiActive) {
        // Download the actual receipt file from Drive
        fileBytes = await GoogleDriveService.downloadFile(widget.file.id!);
      }

      // Parse the receipt (either using Gemini or smart regex fallback)
      final extractedData = await AiParsingService.parseReceipt(
        fileName: widget.file.name ?? 'receipt.pdf',
        mimeType: widget.file.mimeType ?? 'application/pdf',
        fileBytes: fileBytes,
      );

      if (mounted) {
        setState(() {
          _titleController.text = extractedData['title'] ?? '';
          _dateController.text = extractedData['date'] ?? '';
          _odometerController.text =
              extractedData['odometer']?.toString() ?? '';
          _costController.text = extractedData['cost']?.toString() ?? '';
          _descController.text = extractedData['description'] ?? '';
          _isLoading = false;
        });
      }
    } catch (e) {
      print('Workflow parsing failed: $e');
      if (mounted) {
        setState(() {
          _errorMessage =
              'Failed to extract receipt details. You can still enter details manually.';
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _selectDate() async {
    DateTime initial = DateTime.now();
    try {
      if (_dateController.text.isNotEmpty) {
        initial = DateTime.parse(_dateController.text);
      }
    } catch (_) {}

    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );

    if (picked != null && mounted) {
      setState(() {
        _dateController.text = picked.toIso8601String().split('T')[0];
      });
    }
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedCarId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please add and select a car first.')),
      );
      return;
    }

    final double? cost = double.tryParse(_costController.text);
    final int odometer = int.tryParse(_odometerController.text) ?? 0;
    final DateTime date =
        DateTime.tryParse(_dateController.text) ?? DateTime.now();

    final record = MaintenanceRecord(
      id: const Uuid().v4(),
      carId: _selectedCarId!,
      title: _titleController.text.trim(),
      date: date,
      odometer: odometer,
      cost: cost,
      description: _descController.text.trim(),
      driveFileId: widget.file.id,
    );

    widget.onImportSuccess(record);
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isGeminiActive = StorageService.getGeminiApiKey() != null;

    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: theme.colorScheme.outlineVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Icon(
                  widget.file.mimeType == 'application/pdf'
                      ? Icons.picture_as_pdf
                      : Icons.image,
                  color: theme.colorScheme.primary,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Review and Sync',
                    style: theme.textTheme.titleLarge,
                  ),
                ),
              ],
            ),
            Text(
              widget.file.name ?? 'Receipt File',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.outline,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            const Divider(height: 24),

            if (_isLoading)
              Center(
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 40.0),
                  child: Column(
                    children: [
                      const CircularProgressIndicator(),
                      const SizedBox(height: 16),
                      Text(
                        isGeminiActive
                            ? 'Downloading receipt and running Gemini AI extraction...'
                            : 'Scanning filename for offline data extraction...',
                        style: theme.textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
              )
            else ...[
              if (_errorMessage != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12.0),
                  child: Card(
                    color: theme.colorScheme.errorContainer,
                    child: Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: Row(
                        children: [
                          Icon(
                            Icons.error_outline,
                            color: theme.colorScheme.error,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _errorMessage!,
                              style: TextStyle(
                                color: theme.colorScheme.onErrorContainer,
                                fontSize: 12,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),

              // Car Selector
              DropdownButtonFormField<String>(
                initialValue: _selectedCarId,
                decoration: const InputDecoration(
                  labelText: 'Assign to Car',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.directions_car),
                ),
                items: widget.cars.map((car) {
                  return DropdownMenuItem<String>(
                    value: car.id,
                    child: Text('${car.year} ${car.make} ${car.model}'),
                  );
                }).toList(),
                onChanged: (val) {
                  setState(() {
                    _selectedCarId = val;
                  });
                },
                validator: (val) => val == null ? 'Please select a car' : null,
              ),
              const SizedBox(height: 12),

              // Title
              TextFormField(
                controller: _titleController,
                decoration: const InputDecoration(
                  labelText: 'Service Title',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.title),
                ),
                validator: (val) =>
                    val == null || val.isEmpty ? 'Title is required' : null,
              ),
              const SizedBox(height: 12),

              // Date Picker Field
              TextFormField(
                controller: _dateController,
                readOnly: true,
                decoration: const InputDecoration(
                  labelText: 'Service Date',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.calendar_month),
                ),
                onTap: _selectDate,
                validator: (val) =>
                    val == null || val.isEmpty ? 'Date is required' : null,
              ),
              const SizedBox(height: 12),

              Row(
                children: [
                  // Odometer
                  Expanded(
                    child: TextFormField(
                      controller: _odometerController,
                      decoration: const InputDecoration(
                        labelText: 'Odometer (mi)',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.speed),
                      ),
                      keyboardType: TextInputType.number,
                      validator: (val) => val == null || val.isEmpty
                          ? 'Odometer is required'
                          : null,
                    ),
                  ),
                  const SizedBox(width: 12),
                  // Cost
                  Expanded(
                    child: TextFormField(
                      controller: _costController,
                      decoration: const InputDecoration(
                        labelText: 'Total Cost (\$)',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.attach_money),
                      ),
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // Description
              TextFormField(
                controller: _descController,
                decoration: const InputDecoration(
                  labelText: 'Service Details / Description',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.description),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: 20),

              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Cancel'),
                  ),
                  const SizedBox(width: 8),
                  FilledButton.icon(
                    onPressed: _submit,
                    icon: const Icon(Icons.cloud_done),
                    label: const Text('Sync and Import'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
