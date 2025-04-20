// 
// Source JavaScript file for Laravel Plugin Tests
//
document.addEventListener('DOMContentLoaded', function() {
    console.log('Laravel Plugin Test JavaScript File');
    
    // Sample function
    function initApp() {
        const elements = document.querySelectorAll('.dynamic-element');
        
        elements.forEach(element => {
            element.addEventListener('click', function() {
                this.classList.toggle('active');
            });
        });
    }
    
    // Initialize
    initApp();
});
