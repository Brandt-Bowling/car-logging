import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import '../models/car.dart';
import '../models/maintenance_record.dart';
import '../services/storage_service.dart';

enum MaintenanceRecordAction { saved, deleted }

class MaintenanceRecordDialogResult {
  final MaintenanceRecordAction action;
  final MaintenanceRecord? record;

  const MaintenanceRecordDialogResult({
    required this.action,
    this.record,
  });
}

Future<MaintenanceRecordDialogResult?> showMaintenanceRecordDialog(
  BuildContext context, {
  required Car car,
  MaintenanceRecord? record,
  ValueChanged<Car>? onCarUpdated,
}) {
  return showDialog<MaintenanceRecordDialogResult>(
    context: context,
    builder: (context) => MaintenanceRecordDialog(
      car: car,
      record: record,
      onCarUpdated: onCarUpdated,
    ),
  );
}

class MaintenanceRecordDialog extends StatefulWidget {
  final Car car;
  final MaintenanceRecord? record;
  final ValueChanged<Car>? onCarUpdated;

  const MaintenanceRecordDialog({
    super.key,
    required this.car,
    this.record,
    this.onCarUpdated,
  });

  bool get isEditing => record != null;

  @override
  State<MaintenanceRecordDialog> createState() => _MaintenanceRecordDialogState();
}

class _MaintenanceRecordDialogState extends State<MaintenanceRecordDialog> {
  final _formKey = GlobalKey<FormState>();

  late TextEditingController _titleController;
  late TextEditingController _odometerController;
  late TextEditingController _costController;
  late TextEditingController _descriptionController;
  late DateTime _selectedDate;

  @override
  void initState() {
    super.initState();
    final rec = widget.record;
    _titleController = TextEditingController(text: rec?.title ?? '');
    _odometerController = TextEditingController(
      text: rec != null
          ? rec.odometer.toString()
          : (widget.car.odometer != null ? widget.car.odometer.toString() : ''),
    );
    _costController = TextEditingController(
      text: rec?.cost != null
          ? (rec!.cost! % 1 == 0
              ? rec.cost!.toInt().toString()
              : rec.cost!.toStringAsFixed(2))
          : '',
    );
    _descriptionController = TextEditingController(text: rec?.description ?? '');
    _selectedDate = rec?.date ?? DateTime.now();
  }

  @override
  void dispose() {
    _titleController.dispose();
    _odometerController.dispose();
    _costController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate,
      firstDate: DateTime(1990),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (picked != null) {
      setState(() {
        _selectedDate = picked;
      });
    }
  }

  Future<void> _confirmDelete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Record?'),
        content: Text(
          'Are you sure you want to delete "${widget.record?.title}"? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error,
              foregroundColor: Theme.of(ctx).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      await StorageService.deleteMaintenanceRecord(widget.car.id, widget.record!.id);
      if (mounted) {
        Navigator.pop(
          context,
          MaintenanceRecordDialogResult(
            action: MaintenanceRecordAction.deleted,
            record: widget.record,
          ),
        );
      }
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    final title = _titleController.text.trim();
    final odometer = int.parse(_odometerController.text.trim());
    final costText = _costController.text.trim();
    final cost = costText.isNotEmpty ? double.tryParse(costText) : null;
    final description = _descriptionController.text.trim().isNotEmpty
        ? _descriptionController.text.trim()
        : null;

    final MaintenanceRecord savedRecord;
    if (widget.isEditing) {
      savedRecord = widget.record!.copyWith(
        title: title,
        date: _selectedDate,
        odometer: odometer,
        cost: cost,
        description: description,
      );
    } else {
      savedRecord = MaintenanceRecord(
        id: const Uuid().v4(),
        carId: widget.car.id,
        title: title,
        date: _selectedDate,
        odometer: odometer,
        cost: cost,
        description: description,
      );
    }

    await StorageService.addMaintenanceRecord(savedRecord);

    // If odometer entered is higher than current car odometer, update car
    if (widget.car.odometer == null || odometer > widget.car.odometer!) {
      final updatedCar = widget.car.copyWith(odometer: odometer);
      final cars = StorageService.getCars();
      final idx = cars.indexWhere((c) => c.id == widget.car.id);
      if (idx != -1) {
        cars[idx] = updatedCar;
        await StorageService.saveCars(cars);
        widget.onCarUpdated?.call(updatedCar);
      }
    }

    if (mounted) {
      Navigator.pop(
        context,
        MaintenanceRecordDialogResult(
          action: MaintenanceRecordAction.saved,
          record: savedRecord,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDriveImport = widget.record?.driveFileId != null;

    return AlertDialog(
      title: Row(
        children: [
          Icon(
            widget.isEditing ? Icons.edit_note : Icons.add_circle_outline,
            color: theme.colorScheme.primary,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              widget.isEditing ? 'Edit Maintenance Record' : 'Add Maintenance Record',
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (widget.isEditing)
            IconButton(
              icon: const Icon(Icons.delete_outline),
              color: theme.colorScheme.error,
              tooltip: 'Delete Record',
              onPressed: _confirmDelete,
            ),
        ],
      ),
      content: SizedBox(
        width: 400,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (isDriveImport) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    margin: const EdgeInsets.only(bottom: 12),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.tertiaryContainer.withValues(alpha: 0.5),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: theme.colorScheme.tertiaryContainer,
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.cloud_done,
                          size: 16,
                          color: theme.colorScheme.onTertiaryContainer,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            'Synced from Google Drive',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onTertiaryContainer,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                TextFormField(
                  controller: _titleController,
                  decoration: const InputDecoration(
                    labelText: 'Service Title *',
                    hintText: 'e.g. Oil Change, Brake Inspection',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.build_outlined),
                  ),
                  textCapitalization: TextCapitalization.sentences,
                  validator: (val) {
                    if (val == null || val.trim().isEmpty) {
                      return 'Service title is required';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                InkWell(
                  onTap: _pickDate,
                  borderRadius: BorderRadius.circular(4),
                  child: InputDecorator(
                    decoration: const InputDecoration(
                      labelText: 'Date of Service',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.calendar_today),
                      suffixIcon: Icon(Icons.arrow_drop_down),
                    ),
                    child: Text(
                      _selectedDate.toLocal().toString().split(' ')[0],
                      style: theme.textTheme.bodyMedium,
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _odometerController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Odometer Reading (miles) *',
                    hintText: 'e.g. 45000',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.speed),
                  ),
                  validator: (val) {
                    if (val == null || val.trim().isEmpty) {
                      return 'Odometer reading is required';
                    }
                    final odo = int.tryParse(val.trim());
                    if (odo == null || odo < 0) {
                      return 'Please enter a valid odometer number';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _costController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: 'Cost (\$)',
                    hintText: 'e.g. 65.00',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.attach_money),
                  ),
                  validator: (val) {
                    if (val != null && val.trim().isNotEmpty) {
                      final parsed = double.tryParse(val.trim());
                      if (parsed == null || parsed < 0) {
                        return 'Please enter a valid non-negative cost';
                      }
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _descriptionController,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Notes',
                    hintText: 'Optional notes, parts replaced, invoice details...',
                    border: OutlineInputBorder(),
                    alignLabelWithHint: true,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _save,
          child: Text(widget.isEditing ? 'Save Changes' : 'Add Record'),
        ),
      ],
    );
  }
}
