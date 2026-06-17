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
  const GoogleDriveSyncPage({super.key});

  @override
  State<GoogleDriveSyncPage> createState() => _GoogleDriveSyncPageState();
}

class _GoogleDriveSyncPageState extends State<GoogleDriveSyncPage> {
  GoogleSignInAccount? _currentUser;
  bool _isSigningIn = false;
  bool _isLoadingFolders = false;
  bool _isScanningFiles = false;

  List<drive.File> _driveFiles = [];
  Set<String> _importedFileIds = {};
  List<Car> _cars = [];

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
            _driveFiles = [];
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
        _isScanningFiles = false;
      });
    }
  }

  void _configureApiKey() {
    _apiKeyController.text = _apiKey ?? '';
    showDialog(
      context: context,
      builder: (context) {
        final theme = Theme.of(context);
        return AlertDialog(
          title: const Text('Configure Gemini AI'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Enter your Gemini API Key to enable automated receipt parsing. If no key is provided, the app will parse dates, odometer readings, and costs offline using the receipt filenames.',
                  style: TextStyle(fontSize: 13),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.secondaryContainer.withOpacity(0.4),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: theme.colorScheme.secondary.withOpacity(0.2),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.help_outline,
                            size: 16,
                            color: theme.colorScheme.secondary,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            'How to get a free API Key:',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.secondary,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '1. Go to aistudio.google.com\n'
                        '2. Sign in with your Google account\n'
                        '3. Click "Get API Key" -> "Create API Key"\n'
                        '4. Copy the key and paste it below',
                        style: TextStyle(
                          fontSize: 11,
                          height: 1.4,
                          color: theme.colorScheme.onSecondaryContainer,
                        ),
                      ),
                    ],
                  ),
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
      );
    },
  );
}

  final TextEditingController _apiKeyController = TextEditingController();

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
          setState(() {
            _importedFileIds.add(file.id!);
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
              ? 'Active (Gemini 2.5 Flash)'
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
    final hasFolder = _selectedFolderId != null;

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
            else ...[
              Row(
                children: [
                  Icon(
                    hasFolder ? Icons.folder : Icons.folder_off,
                    color: hasFolder ? Colors.amber : theme.colorScheme.outline,
                    size: 40,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          hasFolder ? (_selectedFolderName ?? 'Selected Folder') : 'No Folder Selected',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: hasFolder ? FontWeight.bold : null,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          hasFolder
                              ? 'Invoices and receipts inside this folder will be scanned.'
                              : 'Select a folder containing your vehicle maintenance receipts.',
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
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  FilledButton.tonalIcon(
                    onPressed: _openFolderPicker,
                    icon: const Icon(Icons.folder_open),
                    label: Text(hasFolder ? 'Change Folder' : 'Select Folder'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _openFolderPicker() async {
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (context) => const _FolderPickerDialog(),
    );

    if (result != null && mounted) {
      final id = result['id']!;
      final name = result['name']!;
      _selectFolder(id, name);
    }
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
            leading: Icon(
              fileTypeIcon,
              color: isImported
                  ? theme.colorScheme.outline
                  : theme.colorScheme.primary,
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
  String? _parsingSource;
  String? _parsingError;

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
          _parsingSource = extractedData['source'] as String?;
          _parsingError = extractedData['error'] as String?;
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
              if (_parsingSource != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12.0),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    decoration: BoxDecoration(
                      color: _parsingSource == 'gemini'
                          ? theme.colorScheme.primaryContainer.withOpacity(0.3)
                          : (_parsingError != null
                              ? theme.colorScheme.errorContainer.withOpacity(0.3)
                              : theme.colorScheme.secondaryContainer.withOpacity(0.3)),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: _parsingSource == 'gemini'
                            ? theme.colorScheme.primary.withOpacity(0.2)
                            : (_parsingError != null
                                ? theme.colorScheme.error.withOpacity(0.2)
                                : theme.colorScheme.secondary.withOpacity(0.2)),
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          _parsingSource == 'gemini'
                              ? Icons.auto_awesome
                              : (_parsingError != null ? Icons.warning_amber_rounded : Icons.offline_pin_outlined),
                          size: 18,
                          color: _parsingSource == 'gemini'
                              ? theme.colorScheme.primary
                              : (_parsingError != null ? theme.colorScheme.error : theme.colorScheme.secondary),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                _parsingSource == 'gemini'
                                    ? 'Parsed successfully using Gemini AI ✨'
                                    : (_parsingError != null
                                        ? 'Gemini AI failed. Fell back to offline filename scan.'
                                        : 'Parsed offline using filename scan.'),
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                  color: _parsingSource == 'gemini'
                                      ? theme.colorScheme.onPrimaryContainer
                                      : (_parsingError != null ? theme.colorScheme.onErrorContainer : theme.colorScheme.onSecondaryContainer),
                                ),
                              ),
                              if (_parsingError != null) ...[
                                const SizedBox(height: 4),
                                Text(
                                  _parsingError!,
                                  style: TextStyle(
                                    fontSize: 10,
                                    color: theme.colorScheme.error,
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
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

class _FolderPickerDialog extends StatefulWidget {
  const _FolderPickerDialog();

  @override
  State<_FolderPickerDialog> createState() => _FolderPickerDialogState();
}

class _FolderPickerDialogState extends State<_FolderPickerDialog> {
  final List<Map<String, String>> _pathStack = [
    {'id': 'root', 'name': 'My Drive'}
  ];
  List<drive.File> _folders = [];
  bool _isLoading = false;
  String? _errorMessage;
  final TextEditingController _searchController = TextEditingController();
  bool _isSearching = false;

  @override
  void initState() {
    super.initState();
    _loadCurrentFolders();
  }

  Future<void> _loadCurrentFolders() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final currentFolder = _pathStack.last;
      final parentId = currentFolder['id']!;
      final folders = await GoogleDriveService.listFolders(
        parentId: parentId,
        searchName: _isSearching ? _searchController.text : null,
      );
      setState(() {
        _folders = folders;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to load folders: $e';
        _isLoading = false;
      });
    }
  }

  void _navigateToFolder(String id, String name) {
    if (_isSearching) {
      setState(() {
        _isSearching = false;
        _searchController.clear();
        _pathStack.clear();
        _pathStack.add({'id': 'root', 'name': 'My Drive'});
        _pathStack.add({'id': id, 'name': name});
      });
    } else {
      setState(() {
        _pathStack.add({'id': id, 'name': name});
      });
    }
    _loadCurrentFolders();
  }

  void _navigateUp() {
    if (_pathStack.length > 1) {
      setState(() {
        _pathStack.removeLast();
      });
      _loadCurrentFolders();
    }
  }

  void _jumpToPathIndex(int index) {
    if (index >= 0 && index < _pathStack.length) {
      setState(() {
        _pathStack.removeRange(index + 1, _pathStack.length);
      });
      _loadCurrentFolders();
    }
  }

  void _onSearchChanged() {
    final query = _searchController.text.trim();
    setState(() {
      _isSearching = query.isNotEmpty;
    });
    _loadCurrentFolders();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final currentFolderName = _pathStack.last['name']!;

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Container(
        width: MediaQuery.of(context).size.width * 0.9,
        height: MediaQuery.of(context).size.height * 0.7,
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Select Google Drive Folder',
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.pop(context),
                ),
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Search folders by name...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _searchController.clear();
                          _onSearchChanged();
                        },
                      )
                    : null,
                border: const OutlineInputBorder(
                  borderRadius: BorderRadius.all(Radius.circular(12)),
                ),
                contentPadding: const EdgeInsets.symmetric(vertical: 8),
              ),
              onChanged: (_) => _onSearchChanged(),
            ),
            const SizedBox(height: 12),
            if (!_isSearching)
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: List.generate(_pathStack.length, (index) {
                    final isLast = index == _pathStack.length - 1;
                    final folder = _pathStack[index];
                    return Row(
                      children: [
                        if (index > 0)
                          Icon(Icons.chevron_right, size: 16, color: theme.colorScheme.outline),
                        InkWell(
                          onTap: isLast ? null : () => _jumpToPathIndex(index),
                          borderRadius: BorderRadius.circular(4),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                            child: Text(
                              folder['name']!,
                              style: TextStyle(
                                color: isLast ? theme.colorScheme.primary : theme.colorScheme.outline,
                                fontWeight: isLast ? FontWeight.bold : FontWeight.normal,
                              ),
                            ),
                          ),
                        ),
                      ],
                    );
                  }),
                ),
              )
            else
              Text(
                'Search Results',
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline),
              ),
            const Divider(height: 16),
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : _errorMessage != null
                      ? Center(child: Text(_errorMessage!, style: TextStyle(color: theme.colorScheme.error)))
                      : _folders.isEmpty
                          ? Center(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Icon(Icons.folder_open, size: 48, color: theme.colorScheme.outline),
                                  const SizedBox(height: 8),
                                  Text(
                                    _isSearching ? 'No matching folders found.' : 'No subfolders found here.',
                                    style: TextStyle(color: theme.colorScheme.outline),
                                  ),
                                ],
                              ),
                            )
                          : ListView.builder(
                              itemCount: _folders.length,
                              itemBuilder: (context, index) {
                                final folder = _folders[index];
                                return ListTile(
                                  leading: const Icon(Icons.folder, color: Colors.amber),
                                  title: Text(folder.name ?? 'Unnamed Folder'),
                                  trailing: const Icon(Icons.chevron_right, size: 16),
                                  onTap: () => _navigateToFolder(folder.id!, folder.name ?? 'Drive Folder'),
                                );
                              },
                            ),
            ),
            const Divider(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                if (!_isSearching && _pathStack.length > 1)
                  TextButton.icon(
                    onPressed: _navigateUp,
                    icon: const Icon(Icons.arrow_upward, size: 16),
                    label: const Text('Back'),
                  )
                else
                  const SizedBox(),
                FilledButton(
                  onPressed: _isSearching
                      ? null
                      : () {
                          final currentFolder = _pathStack.last;
                          Navigator.pop(context, currentFolder);
                        },
                  child: Text(
                    _isSearching ? 'Select a folder' : 'Select "$currentFolderName"',
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
