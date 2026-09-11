import 'dart:async';
import 'package:flutter/material.dart';
import 'package:googleapis/drive/v3.dart' as drive;

import '../services/google_drive_service.dart';

/// Data class representing a user-selected Google Drive folder.
class DriveFolderSelection {
  final String id;
  final String name;
  final String path;

  const DriveFolderSelection({
    required this.id,
    required this.name,
    required this.path,
  });

  @override
  String toString() => 'DriveFolderSelection(id: $id, name: $name, path: $path)';
}

typedef FolderFetcher = Future<List<drive.File>> Function({
  String parentId,
  String? searchName,
});

/// Helper function to display the DriveFolderPickerSheet as a modal bottom sheet.
Future<DriveFolderSelection?> showDriveFolderPicker(
  BuildContext context, {
  String? currentFolderId,
  String? currentFolderName,
  FolderFetcher? folderFetcher,
}) {
  return showModalBottomSheet<DriveFolderSelection>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (context) => DriveFolderPickerSheet(
      currentFolderId: currentFolderId,
      currentFolderName: currentFolderName,
      folderFetcher: folderFetcher,
    ),
  );
}

class _BreadcrumbItem {
  final String id;
  final String name;

  const _BreadcrumbItem({required this.id, required this.name});
}

class DriveFolderPickerSheet extends StatefulWidget {
  final String? currentFolderId;
  final String? currentFolderName;
  final FolderFetcher? folderFetcher;

  const DriveFolderPickerSheet({
    super.key,
    this.currentFolderId,
    this.currentFolderName,
    this.folderFetcher,
  });

  @override
  State<DriveFolderPickerSheet> createState() => _DriveFolderPickerSheetState();
}

class _DriveFolderPickerSheetState extends State<DriveFolderPickerSheet> {
  // Navigation stack
  List<_BreadcrumbItem> _breadcrumbs = [
    const _BreadcrumbItem(id: 'root', name: 'My Drive'),
  ];

  // Current folder listing state
  List<drive.File> _folders = [];
  bool _isLoading = false;
  String? _errorMessage;

  // Search state
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _breadcrumbScrollController = ScrollController();
  Timer? _searchDebounce;
  bool _isSearching = false;
  bool _isSearchingLoading = false;
  List<drive.File> _searchResults = [];

  _BreadcrumbItem get _currentBreadcrumb => _breadcrumbs.last;

  /// Returns the human-readable path of the currently browsed folder,
  /// e.g. "cars / sonic", or empty string if at root "My Drive".
  String get _currentFolderPath {
    if (_breadcrumbs.length <= 1) return '';
    return _breadcrumbs.skip(1).map((b) => b.name).join(' / ');
  }

  @override
  void initState() {
    super.initState();
    _loadCurrentFolder();
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _searchController.dispose();
    _breadcrumbScrollController.dispose();
    super.dispose();
  }

  Future<void> _loadCurrentFolder() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final fetcher = widget.folderFetcher ?? GoogleDriveService.listFolders;
      final folders = await fetcher(
        parentId: _currentBreadcrumb.id,
      );
      folders.sort(
        (a, b) =>
            (a.name ?? '').toLowerCase().compareTo((b.name ?? '').toLowerCase()),
      );

      if (mounted) {
        setState(() {
          _folders = folders;
          _isLoading = false;
        });
        _scrollBreadcrumbsToEnd();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = 'Failed to load folders: $e';
          _isLoading = false;
        });
      }
    }
  }

  void _scrollBreadcrumbsToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_breadcrumbScrollController.hasClients) {
        _breadcrumbScrollController.animateTo(
          _breadcrumbScrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _navigateToSubfolder(drive.File folder) {
    if (folder.id == null) return;
    setState(() {
      _breadcrumbs.add(
        _BreadcrumbItem(id: folder.id!, name: folder.name ?? 'Folder'),
      );
      _searchController.clear();
      _isSearching = false;
    });
    _loadCurrentFolder();
  }

  void _navigateUp() {
    if (_breadcrumbs.length > 1) {
      setState(() {
        _breadcrumbs.removeLast();
        _searchController.clear();
        _isSearching = false;
      });
      _loadCurrentFolder();
    }
  }

  void _navigateToBreadcrumbIndex(int index) {
    if (index >= 0 && index < _breadcrumbs.length - 1) {
      setState(() {
        _breadcrumbs = _breadcrumbs.sublist(0, index + 1);
        _searchController.clear();
        _isSearching = false;
      });
      _loadCurrentFolder();
    }
  }

  void _onSearchChanged(String query) {
    _searchDebounce?.cancel();
    final trimmed = query.trim();
    if (trimmed.isEmpty) {
      setState(() {
        _isSearching = false;
        _isSearchingLoading = false;
        _searchResults = [];
      });
      return;
    }

    setState(() {
      _isSearching = true;
      _isSearchingLoading = true;
    });

    _searchDebounce = Timer(const Duration(milliseconds: 350), () async {
      try {
        final fetcher = widget.folderFetcher ?? GoogleDriveService.listFolders;
        final results = await fetcher(
          searchName: trimmed,
        );
        results.sort(
          (a, b) =>
              (a.name ?? '')
                  .toLowerCase()
                  .compareTo((b.name ?? '').toLowerCase()),
        );
        if (mounted) {
          setState(() {
            _searchResults = results;
            _isSearchingLoading = false;
          });
        }
      } catch (e) {
        if (mounted) {
          setState(() {
            _isSearchingLoading = false;
          });
        }
      }
    });
  }

  void _selectCurrentFolder() {
    if (_breadcrumbs.length <= 1) return;
    final selection = DriveFolderSelection(
      id: _currentBreadcrumb.id,
      name: _currentBreadcrumb.name,
      path: _currentFolderPath,
    );
    Navigator.of(context).pop(selection);
  }

  void _selectFolderItem(drive.File folder) {
    if (folder.id == null) return;
    final folderName = folder.name ?? 'Drive Folder';
    final path = _isSearching
        ? folderName
        : (_currentFolderPath.isEmpty
            ? folderName
            : '$_currentFolderPath / $folderName');

    final selection = DriveFolderSelection(
      id: folder.id!,
      name: folderName,
      path: path,
    );
    Navigator.of(context).pop(selection);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isRoot = _breadcrumbs.length <= 1;

    return Material(
      color: theme.colorScheme.surface,
      borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      clipBehavior: Clip.antiAlias,
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.85,
        child: Column(
        children: [
          // Drag Handle
          Center(
            child: Container(
              margin: const EdgeInsets.only(top: 12, bottom: 8),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: theme.colorScheme.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),

          // Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Row(
              children: [
                Icon(
                  Icons.folder_shared_outlined,
                  color: theme.colorScheme.primary,
                  size: 24,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Select Sync Folder',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      if (widget.currentFolderName != null)
                        Text(
                          'Current: ${widget.currentFolderName}',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.outline,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  tooltip: 'Close',
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),

          // Search Field
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
            child: TextField(
              controller: _searchController,
              onChanged: _onSearchChanged,
              decoration: InputDecoration(
                hintText: 'Search all folders in Google Drive...',
                prefixIcon: const Icon(Icons.search, size: 20),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _searchController.clear();
                          _onSearchChanged('');
                        },
                      )
                    : null,
                filled: true,
                fillColor: theme.colorScheme.surfaceContainerHighest.withValues(
                  alpha: 0.5,
                ),
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 10,
                ),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),

          // Breadcrumb Navigation Bar (only in browse mode)
          if (!_isSearching)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              color: theme.colorScheme.surfaceContainerHighest.withValues(
                alpha: 0.3,
              ),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_upward_rounded, size: 20),
                    tooltip: 'Go to parent folder',
                    onPressed: isRoot ? null : _navigateUp,
                  ),
                  Expanded(
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      controller: _breadcrumbScrollController,
                      child: Row(
                        children: List.generate(_breadcrumbs.length, (index) {
                          final item = _breadcrumbs[index];
                          final isLast = index == _breadcrumbs.length - 1;
                          return Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              InkWell(
                                onTap: isLast
                                    ? null
                                    : () => _navigateToBreadcrumbIndex(index),
                                borderRadius: BorderRadius.circular(8),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 6,
                                    vertical: 4,
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      if (index == 0) ...[
                                        Icon(
                                          Icons.cloud_outlined,
                                          size: 16,
                                          color: isLast
                                              ? theme.colorScheme.primary
                                              : theme.colorScheme.outline,
                                        ),
                                        const SizedBox(width: 4),
                                      ],
                                      Text(
                                        item.name,
                                        style: TextStyle(
                                          fontSize: 13,
                                          fontWeight: isLast
                                              ? FontWeight.bold
                                              : FontWeight.normal,
                                          color: isLast
                                              ? theme.colorScheme.primary
                                              : theme.colorScheme.onSurface,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                              if (!isLast)
                                Icon(
                                  Icons.chevron_right,
                                  size: 16,
                                  color: theme.colorScheme.outline,
                                ),
                            ],
                          );
                        }),
                      ),
                    ),
                  ),
                ],
              ),
            ),

          const Divider(height: 1),

          // Main Folder List
          Expanded(
            child: _buildListContent(theme),
          ),

          const Divider(height: 1),

          // Bottom Select Action Bar
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: isRoot && !_isSearching
                  ? OutlinedButton.icon(
                      onPressed: null,
                      icon: const Icon(Icons.touch_app_outlined),
                      label: const Text('Open a folder above to select it'),
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                      ),
                    )
                  : FilledButton.icon(
                      onPressed: _selectCurrentFolder,
                      icon: const Icon(Icons.check_circle_outline),
                      label: Text(
                        _isSearching
                            ? 'Pick from search results above'
                            : 'Use "$_currentFolderPath"',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                      ),
                    ),
            ),
          ),
        ],
      ),
    ),
  );
  }

  Widget _buildListContent(ThemeData theme) {
    if (_isSearching) {
      if (_isSearchingLoading) {
        return const Center(child: CircularProgressIndicator());
      }
      if (_searchResults.isEmpty) {
        return Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.search_off, size: 48, color: theme.colorScheme.outline),
              const SizedBox(height: 12),
              Text(
                'No folders found matching "${_searchController.text}"',
                style: theme.textTheme.bodyMedium,
              ),
            ],
          ),
        );
      }
      return ListView.separated(
        itemCount: _searchResults.length,
        separatorBuilder: (context, index) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final folder = _searchResults[index];
          return _buildFolderTile(folder, theme, isSearchResult: true);
        },
      );
    }

    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.error_outline, size: 48, color: theme.colorScheme.error),
              const SizedBox(height: 12),
              Text(_errorMessage!, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              FilledButton.tonal(
                onPressed: _loadCurrentFolder,
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_folders.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.folder_open_outlined,
                size: 48,
                color: theme.colorScheme.outline,
              ),
              const SizedBox(height: 12),
              Text(
                'No subfolders found in "${_currentBreadcrumb.name}"',
                style: theme.textTheme.titleSmall,
              ),
              const SizedBox(height: 4),
              if (_breadcrumbs.length > 1)
                Text(
                  'Tap the button below to use this folder as your sync source.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.outline,
                  ),
                ),
            ],
          ),
        ),
      );
    }

    return ListView.separated(
      itemCount: _folders.length,
      separatorBuilder: (context, index) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final folder = _folders[index];
        return _buildFolderTile(folder, theme);
      },
    );
  }

  Widget _buildFolderTile(
    drive.File folder,
    ThemeData theme, {
    bool isSearchResult = false,
  }) {
    final isSelectedCurrent = folder.id == widget.currentFolderId;

    return ListTile(
      leading: Icon(
        Icons.folder_rounded,
        color: Colors.amber.shade700,
        size: 32,
      ),
      title: Text(
        folder.name ?? 'Unnamed Folder',
        style: TextStyle(
          fontWeight: isSelectedCurrent ? FontWeight.bold : FontWeight.normal,
        ),
      ),
      subtitle: isSearchResult
          ? Text('Search result', style: theme.textTheme.bodySmall)
          : null,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          OutlinedButton(
            style: OutlinedButton.styleFrom(
              visualDensity: VisualDensity.compact,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
            ),
            onPressed: () => _selectFolderItem(folder),
            child: const Text('Select'),
          ),
          if (!isSearchResult) ...[
            const SizedBox(width: 4),
            const Icon(Icons.chevron_right, size: 20),
          ],
        ],
      ),
      onTap: () {
        if (isSearchResult) {
          _selectFolderItem(folder);
        } else {
          _navigateToSubfolder(folder);
        }
      },
    );
  }
}
