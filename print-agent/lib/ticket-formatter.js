/**
 * Ticket Formatter
 *
 * Converts print job data into ESC/POS commands for thermal printers
 */

class TicketFormatter {
    constructor() {
        this.paperWidth = 48; // Characters per line for 80mm paper
    }

    /**
     * Parse the print data and return a function that executes ESC/POS commands
     */
    format(job) {
        const data = this.parseData(job.printData);
        const paperWidth = job.paperWidth || 80;

        // Adjust character width based on paper size
        this.paperWidth = paperWidth === 58 ? 32 : 48;

        return (printer) => {
            this.printTicket(printer, data, job);
        };
    }

    /**
     * Parse the structured print data from the backend
     */
    parseData(printData) {
        const result = {
            header: {},
            order: {},
            items: [],
            notes: null,
            delivery: null,
            footer: {}
        };

        let currentSection = null;
        const lines = printData.split('\n');

        for (const line of lines) {
            if (line.startsWith('=== ') && line.endsWith(' ===')) {
                currentSection = line.replace(/=== /g, '').replace(/ ===/g, '').toLowerCase();
                continue;
            }

            if (!line.includes(':')) continue;

            const colonIndex = line.indexOf(':');
            const key = line.substring(0, colonIndex).trim();
            const value = line.substring(colonIndex + 1).trim();

            switch (currentSection) {
                case 'header':
                    result.header[key] = value;
                    break;
                case 'order':
                    result.order[key] = value;
                    break;
                case 'items':
                    if (key === 'ITEM') {
                        result.items.push({ name: value, variant: null, instructions: null });
                    } else if (key === 'VARIANT' && result.items.length > 0) {
                        result.items[result.items.length - 1].variant = value;
                    } else if (key === 'INSTRUCTIONS' && result.items.length > 0) {
                        result.items[result.items.length - 1].instructions = value;
                    } else if (key === 'ITEM_COUNT') {
                        result.order.itemCount = value;
                    }
                    break;
                case 'notes':
                    if (key === 'CUSTOMER_NOTES') {
                        result.notes = value;
                    }
                    break;
                case 'delivery':
                    if (!result.delivery) result.delivery = {};
                    result.delivery[key] = value;
                    break;
                case 'footer':
                    result.footer[key] = value;
                    break;
            }
        }

        return result;
    }

    /**
     * Print the ticket using ESC/POS commands
     */
    printTicket(printer, data, job) {
        const divider = '='.repeat(this.paperWidth);
        const thinDivider = '-'.repeat(this.paperWidth);

        // Initialize printer
        printer
            .font('a')
            .align('ct')
            .style('b')
            .size(2, 2);

        // Header - Station name or title
        if (data.header.STATION) {
            printer.text(`*** ${data.header.STATION} ***`);
        } else if (data.header.TITLE) {
            printer.text(`*** ${data.header.TITLE} ***`);
        } else {
            printer.text('*** KITCHEN ORDER ***');
        }

        printer
            .size(1, 1)
            .style('normal')
            .text('')
            .align('lt');

        // Order info
        printer
            .style('b')
            .text(`ORDER #${data.order.ORDER_NUMBER || job.orderNumber || 'N/A'}`)
            .style('normal');

        if (data.order.RESTAURANT) {
            printer.text(data.order.RESTAURANT);
        }

        if (data.order.DATE_TIME) {
            printer.text(data.order.DATE_TIME);
        }

        if (data.order.ORDER_TYPE) {
            printer.text(`Type: ${data.order.ORDER_TYPE}`);
        }

        // Table number - make it prominent
        if (data.order.TABLE) {
            printer
                .text('')
                .style('b')
                .size(1, 2)
                .text(`TABLE: ${data.order.TABLE}`)
                .size(1, 1)
                .style('normal');
        }

        if (data.order.SECTION) {
            printer.text(`Section: ${data.order.SECTION}`);
        }

        // Divider
        printer
            .text('')
            .text(divider)
            .text('');

        // Items header
        const itemCount = data.items.length || data.order.itemCount || '?';
        printer
            .style('b')
            .text(`ITEMS (${itemCount}):`)
            .style('normal')
            .text('');

        // Print each item
        for (const item of data.items) {
            printer.style('b').text(item.name).style('normal');

            if (item.variant) {
                printer.text(`  (${item.variant})`);
            }

            if (item.instructions) {
                printer.text(`  >> ${item.instructions}`);
            }

            printer.text('');
        }

        // Divider
        printer.text(divider);

        // Customer notes
        if (data.notes) {
            printer
                .text('')
                .style('b')
                .text('NOTES:')
                .style('normal')
                .text(data.notes)
                .text(divider);
        }

        // Delivery info
        if (data.delivery) {
            printer
                .text('')
                .style('b')
                .text('DELIVERY:')
                .style('normal');

            if (data.delivery.CONTACT_NAME) {
                printer.text(`Name: ${data.delivery.CONTACT_NAME}`);
            }
            if (data.delivery.CONTACT_PHONE) {
                printer.text(`Phone: ${data.delivery.CONTACT_PHONE}`);
            }
            if (data.delivery.ADDRESS) {
                printer.text(`Address: ${data.delivery.ADDRESS}`);
            }

            printer.text(divider);
        }

        // Footer
        printer
            .text('')
            .align('ct')
            .style('b')
            .size(2, 2);

        if (data.footer.MESSAGE) {
            printer.text(data.footer.MESSAGE);
        } else {
            printer.text('PREPARE NOW!');
        }

        // Feed and finish
        printer
            .size(1, 1)
            .style('normal')
            .text('')
            .text('')
            .text('');
    }

    /**
     * Center text
     */
    centerText(text, width) {
        if (text.length >= width) return text;
        const padding = Math.floor((width - text.length) / 2);
        return ' '.repeat(padding) + text;
    }

    /**
     * Right align text
     */
    rightAlign(text, width) {
        if (text.length >= width) return text;
        const padding = width - text.length;
        return ' '.repeat(padding) + text;
    }
}

module.exports = TicketFormatter;
