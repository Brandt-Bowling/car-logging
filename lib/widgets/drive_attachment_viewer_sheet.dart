import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:googleapis/drive/v3.dart' as drive;
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../services/google_drive_service.dart';

/// Opens the Drive Attachment Viewer as a bottom sheet.
Future<void> showDriveAttachmentViewerSheet(
  BuildContext context, {
  String? driveFileId,
  String? receiptUrl,
  required String recordTitle,
}) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (context) => DriveAttachmentViewerSheet(
      driveFileId: driveFileId,
      receiptUrl: receiptUrl,
      recordTitle: recordTitle,
    ),
  );
}

class DriveAttachmentViewerSheet extends StatefulWidget {
  final String? driveFileId;
  final String? receiptUrl;
  final String recordTitle;
  final drive.File? initialDriveFile;
  final Uint8List? initialImageBytes;

  const DriveAttachmentViewerSheet({
    super.key,
    this.driveFileId,
    this.receiptUrl,
    required this.recordTitle,
    this.initialDriveFile,
    this.initialImageBytes,
  });

  @override
  State<DriveAttachmentViewerSheet> createState() => _DriveAttachmentViewerSheetState();
}

class _DriveAttachmentViewerSheetState extends State<DriveAttachmentViewerSheet> {
  bool _isLoading = true;
  String? _errorMessage;
  drive.File? _driveFile;
  Uint8List? _imageBytes;
  bool _isSignedIn = false;
  bool _isSigningIn = false;

  @override
  void initState() {
    super.initState();
    if (widget.initialDriveFile != null) {
      _driveFile = widget.initialDriveFile;
      _imageBytes = widget.initialImageBytes;
      _isSignedIn = true;
      _isLoading = false;
    } else {
      _checkAndLoadAttachment();
    }
  }

  Future<void> _checkAndLoadAttachment() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    if (widget.driveFileId != null && widget.driveFileId!.isNotEmpty) {
      final user = GoogleDriveService.currentUser;
      if (user != null) {
        _isSignedIn = true;
      } else {
        try {
          final signedIn = await GoogleDriveService.isSignedIn()
              .timeout(const Duration(milliseconds: 200));
          if (signedIn) {
            _isSignedIn = true;
          } else {
            final account = await GoogleDriveService.signInSilently()
                .timeout(const Duration(milliseconds: 200));
            _isSignedIn = account != null;
          }
        } catch (_) {
          _isSignedIn = false;
        }
      }

      if (!_isSignedIn) {
        if (mounted) {
          setState(() {
            _isLoading = false;
          });
        }
        return;
      }

      try {
        final file = await GoogleDriveService.getFileInfo(widget.driveFileId!);
        _driveFile = file;

        if (file != null) {
          final mime = (file.mimeType ?? '').toLowerCase();
          final name = (file.name ?? '').toLowerCase();
          final isImage = mime.startsWith('image/') ||
              name.endsWith('.jpg') ||
              name.endsWith('.jpeg') ||
              name.endsWith('.png') ||
              name.endsWith('.webp') ||
              name.endsWith('.gif');

          if (isImage) {
            try {
              final bytes = await GoogleDriveService.downloadFile(widget.driveFileId!);
              _imageBytes = bytes;
            } catch (e) {
              print('Could not pre-download image bytes: $e');
            }
          }
        } else {
          _errorMessage = 'Attachment file not found on Google Drive.';
        }
      } catch (e) {
        _errorMessage = 'Failed to load file from Google Drive: $e';
      }
    }

    if (mounted) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _handleSignIn() async {
    setState(() {
      _isSigningIn = true;
    });
    try {
      final account = await GoogleDriveService.signIn();
      if (account != null) {
        _isSignedIn = true;
        await _checkAndLoadAttachment();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Sign-in failed: $e'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isSigningIn = false;
        });
      }
    }
  }

  Future<void> _openExternalLink(String? url) async {
    if (url == null || url.isEmpty) return;
    final uri = Uri.tryParse(url);
    if (uri != null) {
      try {
        final launched = await launchUrl(uri, mode: LaunchMode.externalApplication);
        if (!launched && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Could not open link in browser.'),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Error launching URL: $e'),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      }
    }
  }

  String _formatFileSize(String? sizeStr) {
    if (sizeStr == null) return '';
    final bytes = int.tryParse(sizeStr);
    if (bytes == null) return '';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDrive = widget.driveFileId != null && widget.driveFileId!.isNotEmpty;

    return DraggableScrollableSheet(
      initialChildSize: 0.75,
      minChildSize: 0.45,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) {
        return Column(
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: Row(
                children: [
                  CircleAvatar(
                    backgroundColor: isDrive
                        ? theme.colorScheme.primaryContainer
                        : theme.colorScheme.secondaryContainer,
                    child: Icon(
                      isDrive ? Icons.cloud_done : Icons.attachment,
                      color: isDrive
                          ? theme.colorScheme.primary
                          : theme.colorScheme.secondary,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Attachment: ${widget.recordTitle}',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          isDrive
                              ? 'Linked from Google Drive'
                              : 'Linked Attachment / Receipt',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.outline,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    tooltip: 'Close',
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),

            // Content body
            Expanded(
              child: _buildBody(scrollController, theme),
            ),
          ],
        );
      },
    );
  }

  Widget _buildBody(ScrollController scrollController, ThemeData theme) {
    if (_isLoading) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Loading attachment...'),
          ],
        ),
      );
    }

    // Google Drive Sign-In Required
    if (widget.driveFileId != null && !_isSignedIn) {
      return Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.cloud_sync_outlined,
                size: 56,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(height: 12),
              Text(
                'Google Drive Sign-In Needed',
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                'This maintenance record is linked to a Google Drive receipt. Sign in to your Google account to preview or open the document.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.outline,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              FilledButton.icon(
                onPressed: _isSigningIn ? null : _handleSignIn,
                icon: _isSigningIn
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.login),
                label: Text(_isSigningIn ? 'Signing in...' : 'Sign In with Google'),
              ),
            ],
          ),
        ),
      );
    }

    // Error State
    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.error_outline,
                size: 48,
                color: theme.colorScheme.error,
              ),
              const SizedBox(height: 12),
              Text(
                _errorMessage!,
                textAlign: TextAlign.center,
                style: TextStyle(color: theme.colorScheme.error),
              ),
              const SizedBox(height: 16),
              FilledButton.tonal(
                onPressed: _checkAndLoadAttachment,
                child: const Text('Try Again'),
              ),
            ],
          ),
        ),
      );
    }

    // Google Drive File Loaded
    if (_driveFile != null) {
      final file = _driveFile!;
      final mime = (file.mimeType ?? '').toLowerCase();
      final isImage = _imageBytes != null;
      final isPdf = mime.contains('pdf');
      final driveWebLink = file.webViewLink ??
          'https://drive.google.com/file/d/${widget.driveFileId}/view';

      return ListView(
        controller: scrollController,
        padding: const EdgeInsets.all(16),
        children: [
          // File Details Card
          Card(
            elevation: 0,
            color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
              side: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        isPdf
                            ? Icons.picture_as_pdf
                            : (isImage ? Icons.image : Icons.description),
                        color: isPdf
                            ? Colors.red
                            : (isImage ? Colors.green : theme.colorScheme.primary),
                        size: 28,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              file.name ?? 'Receipt Document',
                              style: theme.textTheme.titleSmall?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            Row(
                              children: [
                                if (file.size != null) ...[
                                  Text(
                                    _formatFileSize(file.size),
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.colorScheme.outline,
                                    ),
                                  ),
                                  const Text(' • '),
                                ],
                                Text(
                                  isPdf
                                      ? 'PDF Document'
                                      : (isImage ? 'Image' : 'Document'),
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    color: theme.colorScheme.outline,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton.icon(
                          onPressed: () => _openExternalLink(driveWebLink),
                          icon: const Icon(Icons.open_in_new, size: 18),
                          label: const Text('Open in Google Drive'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Image Preview (if image)
          if (isImage) ...[
            Text(
              'Document Preview',
              style: theme.textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Container(
                constraints: const BoxConstraints(maxHeight: 420),
                decoration: BoxDecoration(
                  color: Colors.black12,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: InteractiveViewer(
                  panEnabled: true,
                  minScale: 0.8,
                  maxScale: 4.0,
                  child: Center(
                    child: Image.memory(
                      _imageBytes!,
                      fit: BoxFit.contain,
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Center(
              child: Text(
                'Pinch or drag to zoom and pan',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.outline,
                ),
              ),
            ),
          ] else if (isPdf) ...[
            // PDF Preview Card
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerLow,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              child: Column(
                children: [
                  const Icon(
                    Icons.picture_as_pdf,
                    size: 64,
                    color: Colors.redAccent,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    file.name ?? 'Receipt.pdf',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'PDF files are stored securely on your Google Drive. Tap the button above to view, print, or share the invoice.',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.outline,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      );
    }

    // General URL attachment fallback
    if (widget.receiptUrl != null && widget.receiptUrl!.isNotEmpty) {
      final url = widget.receiptUrl!;
      final isWebUrl = url.startsWith('http://') || url.startsWith('https://');

      return ListView(
        controller: scrollController,
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              leading: const Icon(Icons.link),
              title: const Text('Receipt Link'),
              subtitle: Text(
                url,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: isWebUrl
                  ? IconButton(
                      icon: const Icon(Icons.open_in_new),
                      onPressed: () => _openExternalLink(url),
                    )
                  : null,
            ),
          ),
          if (isWebUrl) ...[
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: () => _openExternalLink(url),
              icon: const Icon(Icons.open_in_browser),
              label: const Text('Open Receipt URL'),
            ),
          ],
        ],
      );
    }

    return const Center(
      child: Text('No attachment data available for this record.'),
    );
  }
}
