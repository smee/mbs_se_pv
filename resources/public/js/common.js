/* jQuery.values: get or set all of the name/value pairs from child input controls   
 * @argument data {array} If included, will populate all child controls.
 * @returns element if data was provided, or array of values if not
*/

$.fn.values = function(data) {
    var els = $(this).find(':input').get();

    if(typeof data != 'object') {
        // return all data
        data = {};

        $.each(els, function() {
            if (this.name && !this.disabled && (this.checked
                            || /select|textarea/i.test(this.nodeName)
                            || /text|hidden|password|number/i.test(this.type))) {
                data[this.name] = $(this).val();
            }
        });
        return data;
    } else {
        $.each(els, function() {
            if (this.name && data[this.name]) {
                if(this.type == 'checkbox' || this.type == 'radio') {
                    $(this).attr("checked", (data[this.name] == $(this).val()));
                } else {
                    $(this).val(data[this.name]);
                }
            }
        });
        return $(this);
    }
};
window.DateSelector = window.DateSelector || {};
DateSelector.shiftTime = function(days, months, years){
	// FIXME hard coded selectors
	var sf = $('#startDate');
	var ef = $('#endDate');
	
	var startdate = sf.DatePickerGetDate(false);
	var enddate = ef.DatePickerGetDate(false);
	
	startdate.addDays(days);
	startdate.addMonths(months);
	startdate.addYears(years);
	enddate.addDays(days);
	enddate.addMonths(months);
	enddate.addYears(years);
	
	sf.DatePickerSetDate(startdate,true);
	sf.val(sf.DatePickerGetDate(true));
	ef.DatePickerSetDate(enddate,true);
	ef.val(ef.DatePickerGetDate(true));
	// FIXME hard coded selectors		
	if($('#rerender').attr('checked')){
		$('#render-chart').trigger('click');
	}
	
	return false;
}