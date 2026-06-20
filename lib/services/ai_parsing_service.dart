import 'dart:convert';
import 'dart:typed_data';
import 'package:google_generative_ai/google_generative_ai.dart';
import 'storage_service.dart';

class AiParsingService {
  // Main entry point for parsing a file.
  // It will try to use Gemini if an API key is present.
  // If not, or if it fails, it will fall back to parsing the filename using smart regexes.
  static Future<Map<String, dynamic>> parseReceipt({
    required String fileName,
    required String mimeType,
    Uint8List? fileBytes,
  }) async {
    final apiKey = StorageService.getGeminiApiKey();

    if (apiKey != null && apiKey.isNotEmpty && fileBytes != null) {
      try {
        final result = await _parseWithGemini(apiKey, mimeType, fileBytes);
        result['source'] = 'gemini';
        return result;
      } catch (e) {
        print('Gemini parsing failed, falling back to filename parsing: $e');
        final result = _parseFromFileName(fileName);
        result['source'] = 'filename';
        result['error'] = e.toString();
        return result;
      }
    }

    // Offline / Fallback parsing based on the filename
    final result = _parseFromFileName(fileName);
    result['source'] = 'filename';
    return result;
  }

  // Parses receipt image/PDF using Gemini AI
  // Parses receipt image/PDF using Gemini AI
  static Future<Map<String, dynamic>> _parseWithGemini(
    String apiKey,
    String mimeType,
    Uint8List fileBytes,
  ) async {
    const modelName = 'gemini-2.5-flash';
    final model = GenerativeModel(
      model: modelName,
      apiKey: apiKey,
      generationConfig: GenerationConfig(responseMimeType: 'application/json'),
    );

    final systemPrompt = '''
You are an expert car maintenance assistant. Your task is to analyze the provided receipt (which may be an image, PDF, or document) and extract the relevant service fields as a structured JSON object.

Be extremely thorough when looking for odometer and cost details in the document:

1. title: A concise title summarizing the main service (e.g., "Oil Change", "Tire Rotation", "Brake Replacement", "State Inspection", "Battery Replacement").
2. date: The date of the service in YYYY-MM-DD format. Look for dates labeled "Date", "Service Date", "Invoice Date", "Transaction Date". If not found, use today's date.
3. odometer: The odometer reading (miles or km) as an integer.
   - Look for terms like: "Odometer", "Odo", "Odom", "Mileage", "Miles", "Km", "Kilometers", "In Odometer", "Out Odometer", "Vehicle Mileage", "Curr. Odo", "Reading", "Mileage In/Out", "Mileage In", "Mileage Out", "In/Out Mileage", "Odom In", "Odom Out", "Odometer In", "Odometer Out".
   - Look for a 4-to-6 digit number near these terms.
   - Do not include units or decimals. If not found, output null.
4. cost: The total cost charged as a double.
   - Look for terms like: "Total", "Grand Total", "Amount Due", "Total Paid", "Invoice Total", "Net Amount", "Amount", "Balance Due", "Paid", "Balance", "Total Invoice", "Invoice Amount", "Charges", "Total Charge".
   - Check the bottom of the page or the final line of the summary table.
   - Look for currency formatting (e.g., "\$123.45" or "123.45 USD") and extract just the numeric part as a double (e.g., 123.45).
   - If there are multiple totals (e.g., Parts Total, Labor Total), pick the final grand total after tax and discounts.
   - If not found, output null.
5. description: A brief summary of the services performed and any recommendations or findings mentioned (max 2-3 sentences).

Return ONLY a valid JSON object matching the format below:
{
  "title": "...",
  "date": "YYYY-MM-DD",
  "odometer": 12345,
  "cost": 45.67,
  "description": "..."
}
''';

    final content = [
      Content.multi([
        TextPart(systemPrompt),
        DataPart(mimeType, fileBytes),
      ])
    ];

    final response = await model.generateContent(content);
    final text = response.text;

    if (text == null || text.isEmpty) {
      throw Exception('Received empty response from Gemini.');
    }

    var cleanedText = text.trim();
    if (cleanedText.startsWith('```')) {
      final lines = cleanedText.split('\n');
      if (lines.isNotEmpty && lines.first.startsWith('```')) {
        lines.removeAt(0);
      }
      if (lines.isNotEmpty && lines.last.startsWith('```')) {
        lines.removeLast();
      }
      cleanedText = lines.join('\n').trim();
    }

    final parsed = jsonDecode(cleanedText);
    if (parsed is Map) {
      final result = Map<String, dynamic>.from(parsed);
      result['model_used'] = modelName;
      return result;
    }

    throw Exception('Failed to get valid structured JSON from Gemini.');
  }

  // Smart regex-based parser that extracts info from filenames like:
  // "2024-05-15_Oil Change_45000mi_65usd.pdf" or "Oil Change 12-05-2023.jpg"
  static Map<String, dynamic> _parseFromFileName(String fileName) {
    // Remove file extension
    final nameWithoutExt = fileName.contains('.')
        ? fileName.substring(0, fileName.lastIndexOf('.'))
        : fileName;

    // Default values
    String title = nameWithoutExt.replaceAll('_', ' ').replaceAll('-', ' ');
    String dateStr = DateTime.now().toIso8601String().split('T')[0];
    int? odometer;
    double? cost;
    String description = 'Imported from file: $fileName';

    // 1. Try to find a date (YYYY-MM-DD)
    final dateRegex1 = RegExp(r'\b\d{4}[-/_]\d{2}[-/_]\d{2}\b');
    final matchDate1 = dateRegex1.firstMatch(fileName);
    if (matchDate1 != null) {
      dateStr = matchDate1.group(0)!.replaceAll('_', '-').replaceAll('/', '-');
    } else {
      // Try to find a date (MM-DD-YYYY) or (DD-MM-YYYY)
      final dateRegex2 = RegExp(r'\b\d{2}[-/_]\d{2}[-/_]\d{4}\b');
      final matchDate2 = dateRegex2.firstMatch(fileName);
      if (matchDate2 != null) {
        final raw = matchDate2.group(0)!.replaceAll('_', '-').replaceAll('/', '-');
        final parts = raw.split('-');
        if (parts.length == 3) {
          // Check if first part is month or day. We'll assume YYYY-MM-DD format assembly.
          // If first part > 12, it's DD-MM-YYYY
          final p1 = int.tryParse(parts[0]) ?? 1;
          final year = parts[2];
          if (p1 > 12) {
            // DD-MM-YYYY -> YYYY-MM-DD
            dateStr = '$year-${parts[1].padLeft(2, '0')}-${parts[0].padLeft(2, '0')}';
          } else {
            // MM-DD-YYYY -> YYYY-MM-DD
            dateStr = '$year-${parts[0].padLeft(2, '0')}-${parts[1].padLeft(2, '0')}';
          }
        }
      }
    }

    // 2. Try to find odometer (e.g. "45000mi", "45000 miles", "45k", "45000 mi")
    final odoRegex = RegExp(r'\b(\d+)\s*(?:mi|miles|k|kilometers|km)\b', caseSensitive: false);
    final matchOdo = odoRegex.firstMatch(fileName);
    if (matchOdo != null) {
      final valueStr = matchOdo.group(1)!;
      odometer = int.tryParse(valueStr);
      // If it has 'k', it might be like 45k -> 45000. Let's check original text
      final fullMatch = matchOdo.group(0)!.toLowerCase();
      if (fullMatch.contains('k') && !fullMatch.contains('km')) {
        odometer = (odometer ?? 0) * 1000;
      }
    }

    // 3. Try to find cost (e.g. "$65.50", "65usd", "65.50 dollars", "65_dollars")
    final costRegex = RegExp(r'(?:\$|usd\s*)(\d+(?:\.\d{2})?)\b|\b(\d+(?:\.\d{2})?)\s*(?:\$|usd|dollars)\b', caseSensitive: false);
    final matchCost = costRegex.firstMatch(fileName);
    if (matchCost != null) {
      final valueStr = matchCost.group(1) ?? matchCost.group(2);
      if (valueStr != null) {
        cost = double.tryParse(valueStr);
      }
    }

    // Clean up title (remove dates, odometer readings, and costs from the file name to leave a clean title)
    String cleanedTitle = nameWithoutExt;

    // Remove dates
    cleanedTitle = cleanedTitle.replaceAll(dateRegex1, '').replaceAll(RegExp(r'\b\d{2}[-/_]\d{2}[-/_]\d{4}\b'), '');
    // Remove odometer tokens
    cleanedTitle = cleanedTitle.replaceAll(odoRegex, '');
    // Remove cost tokens
    cleanedTitle = cleanedTitle.replaceAll(costRegex, '');
    // Remove extra separators
    cleanedTitle = cleanedTitle
        .replaceAll(RegExp(r'[-_\s]+'), ' ')
        .trim();

    if (cleanedTitle.isNotEmpty) {
      // Capitalize first letter of each word
      title = cleanedTitle.split(' ').map((word) {
        if (word.isEmpty) return '';
        return word[0].toUpperCase() + word.substring(1).toLowerCase();
      }).join(' ');
    } else {
      title = 'Maintenance Record';
    }

    return {
      'title': title,
      'date': dateStr,
      'odometer': odometer,
      'cost': cost,
      'description': description,
    };
  }
}
