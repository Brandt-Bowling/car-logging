import 'dart:io' show File;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:image_picker/image_picker.dart';
import 'package:uuid/uuid.dart';

import '../data/vehicle_data.dart';
import '../models/car.dart';

class AddCarModal extends StatefulWidget {
  const AddCarModal({super.key});

  @override
  State<AddCarModal> createState() => _AddCarModalState();
}

class _AddCarModalState extends State<AddCarModal> {
  final _formKey = GlobalKey<FormState>();

  // Make / Model selection
  String? _selectedMake;
  String? _selectedModel;
  final _makeController = TextEditingController();
  final _modelController = TextEditingController();
  final _makeFocusNode = FocusNode();
  final _modelFocusNode = FocusNode();

  // Other fields
  int _selectedYear = 2010;
  String? _imageUrl;
  String? _localImagePath;
  String? _licensePlate;
  String? _vin;
  int? _odometer;
  bool _isEv = false;

  // Image picker
  final ImagePicker _picker = ImagePicker();

  // Validation helpers
  bool _makeError = false;
  bool _modelError = false;

  @override
  void dispose() {
    _makeController.dispose();
    _modelController.dispose();
    _makeFocusNode.dispose();
    _modelFocusNode.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Selection handlers
  // ---------------------------------------------------------------------------

  void _onMakeSelected(String make) {
    setState(() {
      _selectedMake = make;
      _selectedModel = null;
      _modelController.clear();
      _imageUrl = null;
      _localImagePath = null;
      _makeError = false;
      // Don't auto-reset EV — user may have toggled it manually
    });
  }

  void _onModelSelected(String model) {
    setState(() {
      _selectedModel = model;
      _modelError = false;

      // Auto-populate default image (if user hasn't picked a custom one)
      if (_selectedMake != null && _localImagePath == null) {
        _imageUrl = getDefaultImageUrl(_selectedMake!, model);
      }

      // Auto-detect EV
      if (_selectedMake != null && isKnownEv(_selectedMake!, model)) {
        _isEv = true;
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Image actions
  // ---------------------------------------------------------------------------

  Future<void> _pickImage() async {
    final XFile? image = await _picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1200,
      maxHeight: 800,
      imageQuality: 85,
    );
    if (image != null) {
      setState(() {
        _localImagePath = image.path;
        _imageUrl = null;
      });
    }
  }

  void _useDefaultImage() {
    if (_selectedMake != null && _selectedModel != null) {
      final url = getDefaultImageUrl(_selectedMake!, _selectedModel!);
      if (url != null) {
        setState(() {
          _imageUrl = url;
          _localImagePath = null;
        });
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('No default image available for this model'),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      }
    }
  }

  void _removeImage() {
    setState(() {
      _imageUrl = null;
      _localImagePath = null;
    });
  }

  // ---------------------------------------------------------------------------
  // Submit
  // ---------------------------------------------------------------------------

  void _submitForm() {
    // Validate make/model (they're not inside TextFormField validators)
    bool hasError = false;
    if (_selectedMake == null || _selectedMake!.isEmpty) {
      setState(() => _makeError = true);
      hasError = true;
    }
    if (_selectedModel == null || _selectedModel!.isEmpty) {
      setState(() => _modelError = true);
      hasError = true;
    }
    if (hasError) return;

    if (_formKey.currentState!.validate()) {
      _formKey.currentState!.save();

      final newCar = Car(
        id: const Uuid().v4(),
        make: _selectedMake!,
        model: _selectedModel!,
        year: _selectedYear,
        imageUrl: _imageUrl,
        localImagePath: _localImagePath,
        licensePlate: _licensePlate?.isEmpty ?? true ? null : _licensePlate,
        vin: _vin?.isEmpty ?? true ? null : _vin,
        odometer: _odometer,
        isEv: _isEv,
      );

      Navigator.pop(context, newCar);
    }
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final allMakes = getAllMakes();
    final currentModels =
        _selectedMake != null ? getModelsForMake(_selectedMake!) : <String>[];
    final hasImage = _imageUrl != null || _localImagePath != null;

    return DraggableScrollableSheet(
      initialChildSize: 0.75,
      minChildSize: 0.4,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) {
        return Container(
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            borderRadius:
                const BorderRadius.vertical(top: Radius.circular(28.0)),
          ),
          child: CustomScrollView(
            controller: scrollController,
            slivers: [
              // Header
              SliverToBoxAdapter(
                child: Column(
                  children: [
                    const SizedBox(height: 12),
                    Container(
                      width: 48,
                      height: 4,
                      decoration: BoxDecoration(
                        color: theme.colorScheme.onSurfaceVariant
                            .withValues(alpha: 0.4),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      'Add New Vehicle',
                      style: theme.textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Select your make and model to get started',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                ),
              ),

              // Form content
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 24.0),
                sliver: SliverToBoxAdapter(
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // ── Make Picker ──
                        _buildAnimatedField(
                          delay: 0.ms,
                          child: _buildMakeField(allMakes, theme),
                        ),
                        const SizedBox(height: 16),

                        // ── Model Picker ──
                        _buildAnimatedField(
                          delay: 100.ms,
                          child: _buildModelField(currentModels, theme),
                        ),
                        const SizedBox(height: 16),

                        // ── Year Picker ──
                        _buildAnimatedField(
                          delay: 200.ms,
                          child: _buildYearField(theme),
                        ),
                        const SizedBox(height: 20),

                        // ── Photo Section ──
                        _buildAnimatedField(
                          delay: 300.ms,
                          child: _buildPhotoSection(theme, hasImage),
                        ),
                        const SizedBox(height: 20),

                        // ── EV Toggle ──
                        _buildAnimatedField(
                          delay: 350.ms,
                          child: _buildEvToggle(theme),
                        ),
                        const SizedBox(height: 16),

                        // ── License Plate ──
                        _buildAnimatedField(
                          delay: 400.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'License Plate',
                              hintText: 'e.g., ABC-1234',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.badge_outlined),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.characters,
                            onSaved: (value) => _licensePlate = value?.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // ── VIN ──
                        _buildAnimatedField(
                          delay: 450.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'VIN',
                              hintText: 'Vehicle Identification Number',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.pin_outlined),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.characters,
                            onSaved: (value) => _vin = value?.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // ── Odometer ──
                        _buildAnimatedField(
                          delay: 500.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'Current Odometer (miles)',
                              hintText: 'e.g., 45000',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.speed),
                            ),
                            keyboardType: TextInputType.number,
                            textInputAction: TextInputAction.done,
                            validator: (value) {
                              if (value != null && value.isNotEmpty) {
                                final val = int.tryParse(value);
                                if (val == null || val < 0) {
                                  return 'Please enter a valid odometer reading';
                                }
                              }
                              return null;
                            },
                            onFieldSubmitted: (_) => _submitForm(),
                            onSaved: (value) {
                              if (value != null && value.isNotEmpty) {
                                _odometer = int.tryParse(value);
                              }
                            },
                          ),
                        ),
                        const SizedBox(height: 32),

                        // ── Submit Button ──
                        _buildAnimatedField(
                          delay: 550.ms,
                          child: FilledButton.icon(
                            onPressed: _submitForm,
                            icon: const Icon(Icons.add),
                            label: const Text('Add Vehicle'),
                            style: FilledButton.styleFrom(
                              padding:
                                  const EdgeInsets.symmetric(vertical: 16),
                            ),
                          ),
                        ),
                        const SizedBox(height: 32),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  // ---------------------------------------------------------------------------
  // Field Builders
  // ---------------------------------------------------------------------------

  /// Searchable Make combobox using Autocomplete.
  Widget _buildMakeField(List<String> allMakes, ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Autocomplete<String>(
          optionsBuilder: (TextEditingValue textEditingValue) {
            if (textEditingValue.text.isEmpty) {
              return allMakes;
            }
            return allMakes.where((make) =>
                make.toLowerCase().contains(textEditingValue.text.toLowerCase()));
          },
          onSelected: (String selection) {
            _makeController.text = selection;
            _onMakeSelected(selection);
          },
          fieldViewBuilder:
              (context, controller, focusNode, onFieldSubmitted) {
            // Sync our controller on first build
            if (_makeController.text.isNotEmpty && controller.text.isEmpty) {
              controller.text = _makeController.text;
            }
            return TextFormField(
              controller: controller,
              focusNode: focusNode,
              decoration: InputDecoration(
                labelText: 'Make *',
                hintText: 'Search makes…',
                border: const OutlineInputBorder(),
                prefixIcon: const Icon(Icons.directions_car_outlined),
                errorText: _makeError ? 'Please select a make' : null,
                suffixIcon: controller.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          controller.clear();
                          setState(() {
                            _selectedMake = null;
                            _selectedModel = null;
                            _modelController.clear();
                            _imageUrl = null;
                            _localImagePath = null;
                            _makeError = false;
                          });
                        },
                      )
                    : null,
              ),
              textInputAction: TextInputAction.next,
              textCapitalization: TextCapitalization.words,
              onChanged: (value) {
                // If user is typing, clear current selection so they must pick
                if (value != _selectedMake) {
                  setState(() {
                    _selectedMake = null;
                    _selectedModel = null;
                    _modelController.clear();
                    _imageUrl = null;
                    _localImagePath = null;
                  });
                }
              },
            );
          },
          optionsViewBuilder: (context, onSelected, options) {
            return _buildOptionsOverlay(options, onSelected, theme);
          },
        ),
      ],
    );
  }

  /// Searchable Model combobox — enabled only when a make is selected.
  Widget _buildModelField(List<String> currentModels, ThemeData theme) {
    final isEnabled = _selectedMake != null;

    return Autocomplete<String>(
      optionsBuilder: (TextEditingValue textEditingValue) {
        if (!isEnabled) return const Iterable<String>.empty();
        if (textEditingValue.text.isEmpty) {
          return currentModels;
        }
        return currentModels.where((model) =>
            model.toLowerCase().contains(textEditingValue.text.toLowerCase()));
      },
      onSelected: (String selection) {
        _modelController.text = selection;
        _onModelSelected(selection);
      },
      fieldViewBuilder: (context, controller, focusNode, onFieldSubmitted) {
        if (_modelController.text.isNotEmpty && controller.text.isEmpty) {
          controller.text = _modelController.text;
        }
        return TextFormField(
          controller: controller,
          focusNode: focusNode,
          enabled: isEnabled,
          decoration: InputDecoration(
            labelText: 'Model *',
            hintText: isEnabled
                ? 'Search ${_selectedMake!} models…'
                : 'Select a make first',
            border: const OutlineInputBorder(),
            prefixIcon: const Icon(Icons.car_repair_outlined),
            errorText: _modelError ? 'Please select a model' : null,
            suffixIcon: controller.text.isNotEmpty && isEnabled
                ? IconButton(
                    icon: const Icon(Icons.clear, size: 18),
                    onPressed: () {
                      controller.clear();
                      setState(() {
                        _selectedModel = null;
                        _imageUrl = null;
                        _localImagePath = null;
                        _modelError = false;
                      });
                    },
                  )
                : null,
          ),
          textInputAction: TextInputAction.next,
          textCapitalization: TextCapitalization.words,
          onChanged: (value) {
            if (value != _selectedModel) {
              setState(() {
                _selectedModel = null;
                _imageUrl = null;
                _localImagePath = null;
              });
            }
          },
        );
      },
      optionsViewBuilder: (context, onSelected, options) {
        return _buildOptionsOverlay(options, onSelected, theme);
      },
    );
  }

  /// Year dropdown — defaults to 2010.
  Widget _buildYearField(ThemeData theme) {
    final currentYear = DateTime.now().year;
    final years =
        List<int>.generate(currentYear - 1990 + 2, (i) => currentYear + 1 - i);

    return DropdownButtonFormField<int>(
      initialValue: _selectedYear,
      decoration: const InputDecoration(
        labelText: 'Year',
        border: OutlineInputBorder(),
        prefixIcon: Icon(Icons.calendar_today_outlined),
      ),
      items: years
          .map((year) => DropdownMenuItem(
                value: year,
                child: Text('$year'),
              ))
          .toList(),
      onChanged: (value) {
        if (value != null) {
          setState(() => _selectedYear = value);
        }
      },
    );
  }

  /// Photo section — preview card with pick / default / remove actions.
  Widget _buildPhotoSection(ThemeData theme, bool hasImage) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: theme.colorScheme.outlineVariant,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Section label
            Row(
              children: [
                Icon(Icons.photo_camera_outlined,
                    size: 20, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: 8),
                Text(
                  'Vehicle Photo',
                  style: theme.textTheme.titleSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Image preview
            AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
              height: hasImage ? 180 : 100,
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(12),
                image: _getPreviewImage(),
              ),
              child: !hasImage
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.directions_car_outlined,
                              size: 36,
                              color: theme.colorScheme.onSurfaceVariant
                                  .withValues(alpha: 0.4)),
                          const SizedBox(height: 8),
                          Text(
                            _selectedModel != null
                                ? 'No image available'
                                : 'Select make & model for preview',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant
                                  .withValues(alpha: 0.6),
                            ),
                          ),
                        ],
                      ),
                    )
                  : null,
            ),
            const SizedBox(height: 12),

            // Action buttons
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _pickImage,
                    icon: const Icon(Icons.photo_library_outlined, size: 18),
                    label: const Text('Choose Photo'),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                if (_selectedMake != null && _selectedModel != null)
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed:
                          _localImagePath != null ? _useDefaultImage : null,
                      icon: const Icon(Icons.auto_fix_high, size: 18),
                      label: const Text('Use Default'),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 10),
                      ),
                    ),
                  ),
                if (hasImage) ...[
                  const SizedBox(width: 8),
                  IconButton.outlined(
                    onPressed: _removeImage,
                    icon: const Icon(Icons.close, size: 18),
                    tooltip: 'Remove image',
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// EV toggle with auto-detection badge.
  Widget _buildEvToggle(ThemeData theme) {
    final isAutoDetected = _selectedMake != null &&
        _selectedModel != null &&
        isKnownEv(_selectedMake!, _selectedModel!);

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: _isEv
              ? theme.colorScheme.primary.withValues(alpha: 0.5)
              : theme.colorScheme.outlineVariant,
        ),
      ),
      child: SwitchListTile(
        title: Row(
          children: [
            const Text('Electric Vehicle (EV)'),
            if (isAutoDetected) ...[
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  'Auto',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onPrimaryContainer,
                  ),
                ),
              ),
            ],
          ],
        ),
        subtitle: const Text('Disables oil change reminders'),
        value: _isEv,
        secondary: Icon(
          Icons.electric_car,
          color: _isEv ? theme.colorScheme.primary : null,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        onChanged: (bool value) {
          setState(() {
            _isEv = value;
          });
        },
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  DecorationImage? _getPreviewImage() {
    ImageProvider? provider;
    if (_localImagePath != null) {
      if (kIsWeb) {
        provider = NetworkImage(_localImagePath!);
      } else {
        provider = FileImage(File(_localImagePath!));
      }
    } else if (_imageUrl != null) {
      provider = NetworkImage(_imageUrl!);
    }
    if (provider == null) return null;
    return DecorationImage(image: provider, fit: BoxFit.cover);
  }

  /// Builds the dropdown overlay for Autocomplete options.
  Widget _buildOptionsOverlay(
    Iterable<String> options,
    AutocompleteOnSelected<String> onSelected,
    ThemeData theme,
  ) {
    return Align(
      alignment: Alignment.topLeft,
      child: Material(
        elevation: 4,
        borderRadius: BorderRadius.circular(12),
        clipBehavior: Clip.antiAlias,
        color: theme.colorScheme.surfaceContainer,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 220, maxWidth: 340),
          child: ListView.builder(
            padding: EdgeInsets.zero,
            shrinkWrap: true,
            itemCount: options.length,
            itemBuilder: (context, index) {
              final option = options.elementAt(index);
              return ListTile(
                dense: true,
                title: Text(option),
                onTap: () => onSelected(option),
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedField(
      {required Widget child, required Duration delay}) {
    return child.animate().fadeIn(duration: 400.ms, delay: delay).slideY(
        begin: 0.2,
        end: 0,
        duration: 400.ms,
        delay: delay,
        curve: Curves.easeOutQuad);
  }
}
