import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/car.dart';
import '../../models/maintenance_record.dart';
import '../../services/storage_service.dart';
import '../../widgets/maintenance_record_dialog.dart';
import '../../widgets/drive_attachment_viewer_sheet.dart';

class MaintenanceTab extends StatefulWidget {
  final Car car;
  final ValueChanged<Car>? onCarUpdated;

  const MaintenanceTab({super.key, required this.car, this.onCarUpdated});

  @override
  State<MaintenanceTab> createState() => MaintenanceTabState();
}

class MaintenanceTabState extends State<MaintenanceTab> {
  List<MaintenanceRecord> _records = [];
  int? _selectedYear; // null means "All Years"
  String _searchQuery = '';
  bool _filterOnlyWithAttachments = false;
  bool _isSearchExpanded = false;
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    loadRecords();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant MaintenanceTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.car.id != widget.car.id || oldWidget.car.odometer != widget.car.odometer) {
      loadRecords();
    }
  }

  void loadRecords() {
    setState(() {
      _records = StorageService.getMaintenanceRecords(widget.car.id);
      // Sort records by date descending (most recent first)
      _records.sort((a, b) => b.date.compareTo(a.date));
    });
  }

  List<int> get _availableYears {
    final years = _records.map((r) => r.date.year).toSet().toList();
    years.sort((a, b) => b.compareTo(a));
    return years;
  }

  List<MaintenanceRecord> get _filteredRecords {
    return _records.where((record) {
      // Filter by selected year
      if (_selectedYear != null && record.date.year != _selectedYear) {
        return false;
      }

      // Filter by drive attachment
      if (_filterOnlyWithAttachments &&
          (record.driveFileId == null || record.driveFileId!.isEmpty) &&
          (record.receiptUrl == null || record.receiptUrl!.isEmpty)) {
        return false;
      }

      // Filter by search query
      if (_searchQuery.trim().isNotEmpty) {
        final query = _searchQuery.toLowerCase().trim();
        final title = record.title.toLowerCase();
        final desc = (record.description ?? '').toLowerCase();
        if (!title.contains(query) && !desc.contains(query)) {
          return false;
        }
      }

      return true;
    }).toList();
  }

  Map<int, List<MaintenanceRecord>> get _groupedRecords {
    final map = <int, List<MaintenanceRecord>>{};
    for (final record in _filteredRecords) {
      final year = record.date.year;
      map.putIfAbsent(year, () => []).add(record);
    }
    return map;
  }

  void _viewAttachment(MaintenanceRecord record) {
    showDriveAttachmentViewerSheet(
      context,
      driveFileId: record.driveFileId,
      receiptUrl: record.receiptUrl,
      recordTitle: record.title,
    );
  }

  Future<void> _editRecord(MaintenanceRecord record) async {
    final result = await showMaintenanceRecordDialog(
      context,
      car: widget.car,
      record: record,
      onCarUpdated: widget.onCarUpdated,
    );

    if (result != null && mounted) {
      loadRecords();
      if (result.action == MaintenanceRecordAction.deleted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Deleted "${record.title}"'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      } else if (result.action == MaintenanceRecordAction.saved) {
        if (result.record != null && result.record!.carId != widget.car.id) {
          final cars = StorageService.getCars();
          final newCar = cars.firstWhere(
            (c) => c.id == result.record!.carId,
            orElse: () => widget.car,
          );
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                'Moved "${result.record!.title}" to ${newCar.year} ${newCar.make} ${newCar.model}',
              ),
              behavior: SnackBarBehavior.floating,
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Updated "${result.record?.title ?? record.title}"'),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      }
    }
  }

  void _showRecordDetailsSheet(MaintenanceRecord record) {
    final theme = Theme.of(context);
    final formattedFullDate = DateFormat.yMMMMd().format(record.date);
    final hasAttachment = (record.driveFileId != null && record.driveFileId!.isNotEmpty) ||
        (record.receiptUrl != null && record.receiptUrl!.isNotEmpty);
    final isDriveImport = record.driveFileId != null && record.driveFileId!.isNotEmpty;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (bottomSheetContext) {
        return DraggableScrollableSheet(
          initialChildSize: 0.6,
          minChildSize: 0.35,
          maxChildSize: 0.9,
          expand: false,
          builder: (context, scrollController) {
            return ListView(
              controller: scrollController,
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              children: [
                // Header
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    CircleAvatar(
                      radius: 24,
                      backgroundColor: isDriveImport
                          ? theme.colorScheme.primaryContainer
                          : theme.colorScheme.secondaryContainer,
                      child: Icon(
                        isDriveImport ? Icons.cloud_done : Icons.build,
                        color: isDriveImport
                            ? theme.colorScheme.primary
                            : theme.colorScheme.secondary,
                        size: 24,
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            record.title,
                            style: theme.textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '$formattedFullDate • ${widget.car.year} ${widget.car.make} ${widget.car.model}',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.outline,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),

                // Key Info Metric Cards
                Row(
                  children: [
                    Expanded(
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4),
                          ),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(
                                  Icons.speed,
                                  size: 16,
                                  color: theme.colorScheme.primary,
                                ),
                                const SizedBox(width: 6),
                                Text(
                                  'Odometer',
                                  style: theme.textTheme.labelMedium?.copyWith(
                                    color: theme.colorScheme.outline,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '${NumberFormat.decimalPattern().format(record.odometer)} mi',
                              style: theme.textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4),
                          ),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(
                                  Icons.attach_money,
                                  size: 16,
                                  color: theme.colorScheme.primary,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  'Service Cost',
                                  style: theme.textTheme.labelMedium?.copyWith(
                                    color: theme.colorScheme.outline,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                              record.cost != null
                                  ? '\$${record.cost!.toStringAsFixed(2)}'
                                  : 'Not specified',
                              style: theme.textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Notes / Description
                if (record.description != null && record.description!.trim().isNotEmpty) ...[
                  Text(
                    'Notes & Details',
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
                      ),
                    ),
                    child: Text(
                      record.description!,
                      style: theme.textTheme.bodyMedium,
                    ),
                  ),
                  const SizedBox(height: 16),
                ],

                // Attachment Section
                Text(
                  'Attachment',
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                if (hasAttachment) ...[
                  Card(
                    elevation: 0,
                    color: isDriveImport
                        ? theme.colorScheme.tertiaryContainer.withValues(alpha: 0.3)
                        : theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(
                        color: isDriveImport
                            ? theme.colorScheme.tertiary.withValues(alpha: 0.3)
                            : theme.colorScheme.outlineVariant,
                      ),
                    ),
                    child: ListTile(
                      leading: Icon(
                        isDriveImport ? Icons.cloud_done : Icons.attachment,
                        color: isDriveImport
                            ? theme.colorScheme.tertiary
                            : theme.colorScheme.primary,
                      ),
                      title: Text(
                        isDriveImport
                            ? 'Google Drive Receipt'
                            : 'Linked Receipt Attachment',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text(
                        isDriveImport
                            ? 'Available to preview or open from Drive'
                            : (record.receiptUrl ?? 'Attachment linked'),
                        style: theme.textTheme.bodySmall,
                      ),
                      trailing: FilledButton.tonalIcon(
                        onPressed: () {
                          Navigator.pop(bottomSheetContext);
                          _viewAttachment(record);
                        },
                        icon: const Icon(Icons.remove_red_eye, size: 16),
                        label: const Text('View'),
                      ),
                    ),
                  ),
                ] else ...[
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: theme.colorScheme.outlineVariant.withValues(alpha: 0.3),
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.attachment_outlined,
                          size: 20,
                          color: theme.colorScheme.outline,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'No attachment linked to this record.',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.outline,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 24),

                // Primary Bottom Actions
                Row(
                  children: [
                    if (hasAttachment) ...[
                      Expanded(
                        child: FilledButton.icon(
                          onPressed: () {
                            Navigator.pop(bottomSheetContext);
                            _viewAttachment(record);
                          },
                          icon: const Icon(Icons.visibility, size: 18),
                          label: const Text('View Attachment'),
                        ),
                      ),
                      const SizedBox(width: 12),
                    ],
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () {
                          Navigator.pop(bottomSheetContext);
                          _editRecord(record);
                        },
                        icon: const Icon(Icons.edit, size: 18),
                        label: const Text('Edit Record'),
                      ),
                    ),
                  ],
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _buildSummaryCard(ThemeData theme) {
    final filtered = _filteredRecords;
    if (filtered.isEmpty) return const SizedBox.shrink();

    final totalSpent = filtered.fold<double>(0.0, (sum, r) => sum + (r.cost ?? 0.0));
    final servicesWithCost = filtered.where((r) => r.cost != null && r.cost! > 0).length;
    final avgCost = servicesWithCost > 0 ? totalSpent / servicesWithCost : 0.0;
    final periodTitle = _selectedYear != null ? 'Year $_selectedYear Summary' : 'All-Time Maintenance';

    return Card(
      elevation: 0,
      color: theme.colorScheme.primaryContainer.withValues(alpha: 0.4),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: theme.colorScheme.primary.withValues(alpha: 0.2),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  periodTitle,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: theme.colorScheme.onPrimaryContainer,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    '${filtered.length} service${filtered.length == 1 ? '' : 's'}',
                    style: theme.textTheme.labelSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                // Total Spent
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Total Spent',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.outline,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '\$${totalSpent.toStringAsFixed(2)}',
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                ),
                // Avg per Service
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Avg / Service',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.outline,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '\$${avgCost.toStringAsFixed(2)}',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildYearFilterBar(ThemeData theme) {
    final years = _availableYears;
    if (years.isEmpty) return const SizedBox.shrink();

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          // All Years Chip
          Padding(
            padding: const EdgeInsets.only(right: 8.0),
            child: ChoiceChip(
              label: Text('All Years (${_records.length})'),
              selected: _selectedYear == null,
              onSelected: (selected) {
                if (selected) {
                  setState(() {
                    _selectedYear = null;
                  });
                }
              },
            ),
          ),
          // Individual Year Chips
          ...years.map((year) {
            final count = _records.where((r) => r.date.year == year).length;
            final isSelected = _selectedYear == year;

            return Padding(
              padding: const EdgeInsets.only(right: 8.0),
              child: ChoiceChip(
                label: Text('$year ($count)'),
                selected: isSelected,
                onSelected: (selected) {
                  setState(() {
                    _selectedYear = selected ? year : null;
                  });
                },
              ),
            );
          }),
        ],
      ),
    );
  }

  Widget _buildRecordCard(MaintenanceRecord record, ThemeData theme) {
    final formattedDate = record.date.toLocal().toString().split(' ')[0];
    final isDriveImport = record.driveFileId != null && record.driveFileId!.isNotEmpty;
    final hasAttachment = isDriveImport ||
        (record.receiptUrl != null && record.receiptUrl!.isNotEmpty);

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        onTap: () => _editRecord(record),
        onLongPress: () => _editRecord(record),
        leading: CircleAvatar(
          backgroundColor: theme.colorScheme.primaryContainer,
          child: Icon(
            isDriveImport ? Icons.cloud_done : Icons.build,
            color: theme.colorScheme.primary,
            size: 20,
          ),
        ),
        title: Text(
          record.title,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('$formattedDate - ${NumberFormat.decimalPattern().format(record.odometer)} mi'),
            if (record.description != null && record.description!.isNotEmpty)
              Text(
                record.description!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12,
                  color: theme.colorScheme.outline,
                ),
              ),
            if (hasAttachment) ...[
              const SizedBox(height: 4),
              InkWell(
                borderRadius: BorderRadius.circular(12),
                onTap: () => _viewAttachment(record),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2.0),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.attachment,
                        size: 14,
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        'View Attachment',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
        trailing: record.cost != null
            ? Text(
                '\$${record.cost!.toStringAsFixed(2)}',
                style: const TextStyle(fontWeight: FontWeight.bold),
              )
            : null,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    // Find next scheduled maintenance (e.g. oil change at next 5,000 mi interval)
    int latestOdometer = widget.car.id == '1' ? 45000 : (widget.car.odometer ?? 0);
    if (_records.isNotEmpty) {
      latestOdometer = _records.map((r) => r.odometer).reduce((a, b) => a > b ? a : b);
    }
    final nextScheduledOdometer = latestOdometer + 5000;
    final groupedRecords = _groupedRecords;
    final sortedGroupYears = groupedRecords.keys.toList()..sort((a, b) => b.compareTo(a));

    return RefreshIndicator(
      onRefresh: () async {
        loadRecords();
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16.0, 20.0, 16.0, 80.0),
        children: [
          // Next Scheduled Maintenance Card
          Card(
            color: theme.colorScheme.secondaryContainer,
            child: ListTile(
              leading: const Icon(Icons.calendar_month),
              title: const Text('Next Scheduled Maintenance'),
              subtitle: Text(
                'Oil Change at ${NumberFormat.decimalPattern().format(nextScheduledOdometer)} miles',
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Year Filter Section Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Maintenance Records',
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              IconButton(
                icon: Icon(_isSearchExpanded ? Icons.close : Icons.search),
                tooltip: _isSearchExpanded ? 'Close Search' : 'Search Records',
                onPressed: () {
                  setState(() {
                    _isSearchExpanded = !_isSearchExpanded;
                    if (!_isSearchExpanded) {
                      _searchQuery = '';
                      _searchController.clear();
                    }
                  });
                },
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Search Box (if expanded)
          if (_isSearchExpanded) ...[
            TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Search service, notes, parts...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchQuery.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          setState(() {
                            _searchQuery = '';
                            _searchController.clear();
                          });
                        },
                      )
                    : null,
                border: const OutlineInputBorder(),
                isDense: true,
              ),
              onChanged: (val) {
                setState(() {
                  _searchQuery = val;
                });
              },
            ),
            const SizedBox(height: 12),
          ],

          // Filter by Year Choice Chips
          if (_records.isNotEmpty) ...[
            _buildYearFilterBar(theme),
            const SizedBox(height: 12),
            // Drive attachment toggle chip
            Row(
              children: [
                FilterChip(
                  avatar: Icon(
                    Icons.attachment,
                    size: 16,
                    color: _filterOnlyWithAttachments
                        ? theme.colorScheme.onPrimary
                        : theme.colorScheme.primary,
                  ),
                  label: const Text('With Attachments'),
                  selected: _filterOnlyWithAttachments,
                  onSelected: (val) {
                    setState(() {
                      _filterOnlyWithAttachments = val;
                    });
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),
            // Period Summary Card
            _buildSummaryCard(theme),
            const SizedBox(height: 16),
          ],

          // Record list / Grouped Year view
          if (_records.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  children: [
                    Icon(
                      Icons.history_toggle_off,
                      size: 48,
                      color: theme.colorScheme.outline,
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'No maintenance history recorded.',
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Tap the floating + button to add one manually or use Google Drive sync to import invoices!',
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            )
          else if (_filteredRecords.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  children: [
                    Icon(
                      Icons.filter_alt_off,
                      size: 44,
                      color: theme.colorScheme.outline,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      _selectedYear != null
                          ? 'No records found for year $_selectedYear.'
                          : 'No matching maintenance records found.',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    FilledButton.tonal(
                      onPressed: () {
                        setState(() {
                          _selectedYear = null;
                          _searchQuery = '';
                          _filterOnlyWithAttachments = false;
                          _searchController.clear();
                        });
                      },
                      child: const Text('Reset Filters'),
                    ),
                  ],
                ),
              ),
            )
          else ...[
            // Render groups by year
            ...sortedGroupYears.map((year) {
              final yearRecords = groupedRecords[year] ?? [];
              final yearTotalCost = yearRecords.fold<double>(
                0.0,
                (sum, r) => sum + (r.cost ?? 0.0),
              );

              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Year Header Banner
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 4.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Text(
                              year.toString(),
                              style: theme.textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                                color: theme.colorScheme.primary,
                              ),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              decoration: BoxDecoration(
                                color: theme.colorScheme.surfaceContainerHighest,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                '${yearRecords.length} record${yearRecords.length == 1 ? '' : 's'}',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (yearTotalCost > 0)
                          Text(
                            '\$${yearTotalCost.toStringAsFixed(2)}',
                            style: theme.textTheme.labelLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.outline,
                            ),
                          ),
                      ],
                    ),
                  ),
                  // Cards for records in this year
                  ...yearRecords.map((record) => _buildRecordCard(record, theme)),
                  const SizedBox(height: 8),
                ],
              );
            }),
          ],
        ],
      ),
    );
  }
}
