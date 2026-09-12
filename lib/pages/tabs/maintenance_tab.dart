import 'package:flutter/material.dart';
import '../../models/car.dart';
import '../../models/maintenance_record.dart';
import '../../services/storage_service.dart';
import '../../widgets/maintenance_record_dialog.dart';

class MaintenanceTab extends StatefulWidget {
  final Car car;
  final ValueChanged<Car>? onCarUpdated;

  const MaintenanceTab({super.key, required this.car, this.onCarUpdated});

  @override
  State<MaintenanceTab> createState() => MaintenanceTabState();
}

class MaintenanceTabState extends State<MaintenanceTab> {
  List<MaintenanceRecord> _records = [];

  @override
  void initState() {
    super.initState();
    loadRecords();
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Updated "${result.record?.title ?? record.title}"'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // Find next scheduled maintenance (e.g. mock oil change at 50,000 mi)
    int latestOdometer = widget.car.id == '1' ? 45000 : 0;
    if (_records.isNotEmpty) {
      latestOdometer = _records.map((r) => r.odometer).reduce((a, b) => a > b ? a : b);
    }
    final nextScheduledOdometer = latestOdometer + 5000;

    return RefreshIndicator(
      onRefresh: () async {
        loadRecords();
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16.0),
        children: [
          Card(
            color: Theme.of(context).colorScheme.secondaryContainer,
            child: ListTile(
              leading: const Icon(Icons.calendar_month),
              title: const Text('Next Scheduled Maintenance'),
              subtitle: Text('Oil Change at ${nextScheduledOdometer.toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]},')} miles'),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'History',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              Text(
                '${_records.length} records',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.outline,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (_records.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  children: [
                    Icon(
                      Icons.history_toggle_off,
                      size: 48,
                      color: Theme.of(context).colorScheme.outline,
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
          else
            ..._records.map((record) {
              final formattedDate = record.date.toLocal().toString().split(' ')[0];
              final isDriveImport = record.driveFileId != null;

              return Card(
                child: ListTile(
                  onTap: () => _editRecord(record),
                  onLongPress: () => _editRecord(record),
                  leading: CircleAvatar(
                    backgroundColor: Theme.of(context).colorScheme.primaryContainer,
                    child: Icon(
                      isDriveImport ? Icons.cloud_done : Icons.build,
                      color: Theme.of(context).colorScheme.primary,
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
                      Text('$formattedDate - ${record.odometer.toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]},')} mi'),
                      if (record.description != null && record.description!.isNotEmpty)
                        Text(
                          record.description!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 12,
                            color: Theme.of(context).colorScheme.outline,
                          ),
                        ),
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
            }),
        ],
      ),
    );
  }
}
