import 'package:flutter/material.dart';

Future<int?> showTireRotationIntervalDialog(
  BuildContext context, {
  required int currentInterval,
}) async {
  final controller = TextEditingController(text: currentInterval.toString());
  final formKey = GlobalKey<FormState>();

  return showDialog<int>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('Tire Rotation Interval'),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Set the mileage interval between scheduled tire rotations.',
                style: TextStyle(fontSize: 14),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: controller,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Interval (miles)',
                  hintText: 'e.g. 7500',
                  suffixText: 'mi',
                  border: OutlineInputBorder(),
                ),
                autofocus: true,
                validator: (val) {
                  if (val == null || val.trim().isEmpty) {
                    return 'Please enter a mileage interval';
                  }
                  final parsed = int.tryParse(val.replaceAll(',', '').trim());
                  if (parsed == null || parsed <= 0) {
                    return 'Please enter a valid positive number';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                children: [5000, 6000, 7500, 10000].map((preset) {
                  return ActionChip(
                    label: Text('$preset mi'),
                    onPressed: () {
                      controller.text = preset.toString();
                    },
                  );
                }).toList(),
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
            onPressed: () {
              if (formKey.currentState?.validate() ?? false) {
                final val = int.tryParse(controller.text.replaceAll(',', '').trim());
                if (val != null && val > 0) {
                  Navigator.pop(context, val);
                }
              }
            },
            child: const Text('Save'),
          ),
        ],
      );
    },
  );
}
